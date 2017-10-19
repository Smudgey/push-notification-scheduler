/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pushnotificationscheduler.actor

import java.util.concurrent.TimeUnit.MINUTES

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.actor.WorkPullingPattern.Epic
import uk.gov.hmrc.pushnotificationscheduler.domain.NativeOS.{Android, Windows, iOS}
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services.{PushRegistrationService, SnsClientService}

import scala.concurrent.Future

class TokenExchangeWorkerSpec extends UnitSpec with MockitoSugar {
  type Work = Seq[RegistrationToken]

  val mockMetrics = mock[Metrics]
  val mockSnsClient = mock[SnsClientService]
  val mockPushRegistration = mock[PushRegistrationService]
  val tokenCaptor = ArgumentCaptor.forClass(classOf[Seq[RegistrationToken]])

  private abstract class Setup extends TestKit(ActorSystem("AkkaTestSystem")) {

    implicit val timeout = Timeout(1, MINUTES)

    val master = system.actorOf(Props[Master[Work]], "master")
    val worker = system.actorOf(TokenExchangeWorker.props(master, mockSnsClient, mockPushRegistration, mockMetrics))
  }

  "When started with an epic" should {
    "exchange registration tokens for endpoints" in new Setup {
      val tokens = List(RegistrationToken("foo", Android), RegistrationToken("bar", Windows), RegistrationToken("baz", iOS))
      val moreTokens = List(RegistrationToken("quux", iOS), RegistrationToken("grault", Android))
      val evenMoreTokens = List(RegistrationToken("froog", iOS))
      val unregisteredTokens = List(tokens, moreTokens, evenMoreTokens)

      val endpoints = Map("foo" -> Some("bish"), "bar" -> Some("bash"), "baz" -> None)
      val moreEndpoints = Map("quux" -> Some("bosh"), "grault" -> None)

      val epic = Epic(unregisteredTokens)

      when(mockSnsClient.exchangeTokens(ArgumentMatchers.eq(tokens))).thenReturn(Future.successful(endpoints))
      when(mockSnsClient.exchangeTokens(ArgumentMatchers.eq(moreTokens))).thenReturn(Future.successful(moreEndpoints))
      when(mockSnsClient.exchangeTokens(ArgumentMatchers.eq(evenMoreTokens))).thenReturn(Future.failed(new HttpException("Wibble", 500)))

      when(mockPushRegistration.registerEndpoints(ArgumentMatchers.eq(endpoints))).thenAnswer(new UpdateSuccess)
      when(mockPushRegistration.registerEndpoints(ArgumentMatchers.eq(moreEndpoints))).thenReturn(Future.failed(new HttpException("Wobble", 500)))

      await(master ? epic)

      verify(mockSnsClient, times(unregisteredTokens.size)).exchangeTokens(tokenCaptor.capture())

      val firstInvocation: Seq[RegistrationToken] = tokenCaptor.getAllValues.get(0)
      val secondInvocation: Seq[RegistrationToken] = tokenCaptor.getAllValues.get(1)

      firstInvocation shouldBe tokens
      secondInvocation shouldBe moreTokens

      verify(mockMetrics).incrementTokenExchangeSuccess(ArgumentMatchers.eq(2L))
      verify(mockMetrics).incrementTokenDisabled(ArgumentMatchers.eq(1L))
      verify(mockMetrics).incrementTokenUpdateFailure(ArgumentMatchers.eq(2L))
      verify(mockMetrics).incrementTokenExchangeFailure(ArgumentMatchers.eq(1L))
    }
  }
}
