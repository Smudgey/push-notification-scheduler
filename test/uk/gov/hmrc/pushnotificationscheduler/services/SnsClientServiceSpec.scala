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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, anyInt}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.http.{HttpReads, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.connectors.SnsClientConnectorApi
import uk.gov.hmrc.pushnotificationscheduler.domain.NativeOS.{Android, Windows}
import uk.gov.hmrc.pushnotificationscheduler.domain.NotificationStatus.{Delivered, Disabled}
import uk.gov.hmrc.pushnotificationscheduler.domain.{Notification, NotificationStatus, RegistrationToken}

import scala.concurrent.{ExecutionContext, Future}

class SnsClientServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {
  private trait Setup extends MockitoSugar {
    val connector = mock[SnsClientConnectorApi]

    val service = new SnsClientService(connector)

    val expectedTokens = List(RegistrationToken("foo", Android), RegistrationToken("bar", Windows))
    val expectedTokenToEndpointMap = Map(expectedTokens map {t => (t.token, Option("/endpoint/" + t.token))} : _*)

    val expectedNotifications = List(
      Notification("msg-1", "end:point:a", "In an immense wood in the south of Kent,"),
      Notification("msg-2", "end:point:b", "There lived a band of robbers which caused the people discontent;")
    )
    val expectedMessageIdToStatusMap = Map("msg-1" -> Delivered, "msg-2" -> Disabled)
  }

  private trait Success extends Setup {
    when(connector.exchangeTokens(any[Seq[RegistrationToken]])(any[HttpReads[Map[String,String]]](), any[ExecutionContext]())).thenReturn(Future.successful(expectedTokenToEndpointMap))
    when(connector.sendNotifications(any[Seq[Notification]])(any[HttpReads[Map[String,NotificationStatus]]](), any[ExecutionContext]())).thenReturn(Future.successful(expectedMessageIdToStatusMap))
  }

  private trait Failed extends Setup {
    when(connector.exchangeTokens(any[Seq[RegistrationToken]])(any[HttpReads[Map[String,String]]](), any[ExecutionContext]())).thenReturn(Future.failed(Upstream5xxResponse("Kaboom!", 500, 500)))
    when(connector.sendNotifications(any[Seq[Notification]])(any[HttpReads[Map[String,NotificationStatus]]](), any[ExecutionContext]())).thenReturn(Future.failed(Upstream5xxResponse("Crash!", 500, 500)))
  }

  "SnsClientService exchangeTokens" should {
    "return a map of tokens to endpoints" in new Success {

      val result = await(service.exchangeTokens(expectedTokens))

      val captor: ArgumentCaptor[Seq[RegistrationToken]] = ArgumentCaptor.forClass(classOf[Seq[RegistrationToken]])
      verify(connector).exchangeTokens(captor.capture())(any[HttpReads[Map[String,String]]](), any[ExecutionContext]())

      captor.getValue shouldBe expectedTokens
      result shouldBe expectedTokenToEndpointMap
    }

    "return an empty map when there is a problem with the SNS client service" in new Failed {

      val result = await(service.exchangeTokens(expectedTokens))

      result.size shouldBe 0
    }
  }

  "SnsClientService sendNotifications" should {
    "return a map of message identifiers to notification status" in new Success {

      val result = await(service.sendNotifications(expectedNotifications))

      val captor: ArgumentCaptor[Seq[Notification]] = ArgumentCaptor.forClass(classOf[Seq[Notification]])
      verify(connector).sendNotifications(captor.capture())(any[HttpReads[Map[String,NotificationStatus]]](), any[ExecutionContext]())

      captor.getValue shouldBe expectedNotifications
      result shouldBe expectedMessageIdToStatusMap
    }

    "return an empty map when there is a problem with the SNS client service" in new Failed {

      val result = await(service.sendNotifications(expectedNotifications))

      result.size shouldBe 0
    }
  }
}