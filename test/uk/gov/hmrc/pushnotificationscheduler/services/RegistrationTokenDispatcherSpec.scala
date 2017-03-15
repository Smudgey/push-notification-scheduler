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

package uk.gov.hmrc.pushnotificationscheduler.services

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.mockito.Mockito.{times, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.domain.NativeOS.{Android, Windows, iOS}
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics

import scala.concurrent.Future
import scala.concurrent.Future.successful

class RegistrationTokenDispatcherSpec extends UnitSpec with ScalaFutures with MockitoSugar with OneAppPerSuite {
  val mockMetrics = mock[Metrics]
  val mockSnsClient = mock[SnsClientService]
  val mockPushRegistration = mock[PushRegistrationService]

  val tokenCaptor = ArgumentCaptor.forClass(classOf[Seq[RegistrationToken]])
  val mapCaptor = ArgumentCaptor.forClass(classOf[Map[String, Option[String]]])

  private abstract class Setup extends TestKit(ActorSystem("AkkaTestSystem")) {
    val unregisteredTokens = List(RegistrationToken("foo", Android), RegistrationToken("bar", Windows), RegistrationToken("baz", iOS))
    val endpoints = Map("foo" -> Some("blip"), "bar" -> None, "baz" -> Some("blop"))

    val dispatcher = new RegistrationTokenDispatcher {
      override def metrics = mockMetrics

      override def pushRegistrationService = mockPushRegistration

      override def snsClientService = mockSnsClient

      override def system = Setup.this.system
    }
  }

  "scheduling the RegistrationToken dispatcher" should {
    "exchange unregistered tokens for endpoints" in new Setup {
      when(mockPushRegistration.getUnregisteredTokens).thenReturn(successful(unregisteredTokens))
      when(mockPushRegistration.recoverFailedRegistrations).thenReturn(successful(Seq.empty))
      when(mockPushRegistration.registerEndpoints(ArgumentMatchers.any[Map[String, Option[String]]]())).thenAnswer(new Answer[Future[_]] {
        override def answer(invocationOnMock: InvocationOnMock): Future[_] = Future.successful(Unit)
      })
      when(mockSnsClient.exchangeTokens(ArgumentMatchers.any[Seq[RegistrationToken]]())).thenReturn(successful(endpoints))

      await(dispatcher.exchangeRegistrationTokensForEndpoints())

      Eventually.eventually {
        Mockito.verify(mockSnsClient).exchangeTokens(tokenCaptor.capture())
        Mockito.verify(mockPushRegistration).registerEndpoints(mapCaptor.capture())

        val actualTokens: Seq[RegistrationToken] = tokenCaptor.getValue
        val acutalMap: Map[String, Option[String]] = mapCaptor.getValue

        actualTokens shouldBe unregisteredTokens
        acutalMap shouldBe endpoints
      }
    }

    "do nothing when there are no unregistered endpoints" in new Setup {
      when(mockPushRegistration.getUnregisteredTokens).thenReturn(successful(Seq.empty))
      when(mockPushRegistration.recoverFailedRegistrations).thenReturn(successful(Seq.empty))

      await(dispatcher.exchangeRegistrationTokensForEndpoints())

      Mockito.verifyZeroInteractions(mockSnsClient)
    }
  }
}
