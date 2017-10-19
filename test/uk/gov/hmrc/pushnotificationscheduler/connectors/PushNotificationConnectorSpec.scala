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

package uk.gov.hmrc.pushnotificationscheduler.connectors

import org.mockito.ArgumentMatchers.{any, matches}
import org.mockito.Mockito.doReturn
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.domain.NotificationStatus.{Delivered, Queued}
import uk.gov.hmrc.pushnotificationscheduler.domain.{Notification, NotificationStatus}
import uk.gov.hmrc.pushnotificationscheduler.support.WithTestApplication

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class PushNotificationConnectorSpec extends UnitSpec with WithTestApplication with ServicesConfig with ScalaFutures {

  private trait Setup extends MockitoSugar {
    val mockHttp: WSHttp = mock[WSHttp]

    val connector = new PushNotificationConnector("http://somewhere:8080", mockHttp)

    val someNotificationWithoutAMessageId = Notification("msg-1", "end:point:1", "hello world", None, "windows")
    val otherNotificationWithAMessageId = Notification("msg-2", "end:point:2", "goodbye", Some("1"), "windows")

    val someStatus = Map("msg-1" -> Delivered, "msg-2" -> Queued)
  }

  private trait Success extends Setup {
    doReturn(successful(Seq(someNotificationWithoutAMessageId, otherNotificationWithAMessageId)), Nil: _*).when(mockHttp).GET[Seq[Notification]](matches(s"${connector.serviceUrl}/notifications/unsent"), any[Seq[(String, String)]]())(any[HttpReads[Seq[Notification]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(successful(Seq(otherNotificationWithAMessageId, someNotificationWithoutAMessageId)), Nil: _*).when(mockHttp).GET[Seq[Notification]](matches(s"${connector.serviceUrl}/notifications/timedout"), any[Seq[(String, String)]]())(any[HttpReads[Seq[Notification]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(successful(HttpResponse(204, None)), Nil: _*).when(mockHttp).POST[Map[String, NotificationStatus], HttpResponse](matches(s"${connector.serviceUrl}/notifications/status"), any[Map[String, NotificationStatus]](), any[Seq[(String, String)]])(any[Writes[Map[String, NotificationStatus]]](), any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])

  }

  private trait BadRequest extends Setup {
    doReturn(failed(new BadRequestException("SPLAT!")), Nil: _*).when(mockHttp).GET[Seq[Notification]](matches(s"${connector.serviceUrl}/notifications/unsent"), any[Seq[(String, String)]]())(any[HttpReads[Seq[Notification]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(failed(new BadRequestException("CRACK!")), Nil: _*).when(mockHttp).GET[Seq[Notification]](matches(s"${connector.serviceUrl}/notifications/timedout"), any[Seq[(String, String)]]())(any[HttpReads[Seq[Notification]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(failed(new BadRequestException("CRASH!")), Nil: _*).when(mockHttp).POST[Map[String, NotificationStatus], HttpResponse](matches(s"${connector.serviceUrl}/notifications/status"), any[Map[String, NotificationStatus]](), any[Seq[(String, String)]])(any[Writes[Map[String, NotificationStatus]]](), any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
  }

  private trait UpstreamFailure extends Setup {
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).GET[Seq[Notification]](matches(s"${connector.serviceUrl}/notifications/unsent"), any[Seq[(String, String)]]())(any[HttpReads[Seq[Notification]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(failed(Upstream5xxResponse("BLAM!", 500, 500)), Nil: _*).when(mockHttp).GET[Seq[Notification]](matches(s"${connector.serviceUrl}/notifications/timedout"), any[Seq[(String, String)]]())(any[HttpReads[Seq[Notification]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(failed(Upstream5xxResponse("BOOM!", 500, 500)), Nil: _*).when(mockHttp).POST[Map[String, NotificationStatus], HttpResponse](matches(s"${connector.serviceUrl}/notifications/status"), any[Map[String, NotificationStatus]](), any[Seq[(String, String)]])(any[Writes[Map[String, NotificationStatus]]](), any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
  }

  "getQueuedNotifications" should {
    "return queued notifications when a 200 response is received with a valid json payload" in new Success {
      val result: Seq[Notification] = await(connector.getQueuedNotifications())

      result.size shouldBe 2
      result.head shouldBe someNotificationWithoutAMessageId
      result(1) shouldBe otherNotificationWithAMessageId
    }

    "throw BadRequestException when a 400 response is returned" in new BadRequest {
      intercept[BadRequestException] {
        await(connector.getQueuedNotifications())
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new UpstreamFailure {
      intercept[Upstream5xxResponse] {
        await(connector.getQueuedNotifications())
      }
    }
  }

  "getTimedOutNotifications" should {
    "return timed-out notifications when a 200 response is received with a valid json payload" in new Success {
      val result: Seq[Notification] = await(connector.getTimedOutNotifications())

      result.size shouldBe 2
      result.head shouldBe otherNotificationWithAMessageId
      result(1) shouldBe someNotificationWithoutAMessageId
    }

    "throw BadRequestException when a 400 response is returned" in new BadRequest {
      intercept[BadRequestException] {
        await(connector.getTimedOutNotifications())
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new UpstreamFailure {
      intercept[Upstream5xxResponse] {
        await(connector.getTimedOutNotifications())
      }
    }
  }

  "updateNotifications" should {
    "return unsent notifications when a 204 response is received with a valid json payload" in new Success {
      val result: Response = await(connector.updateNotifications(someStatus))

      result.status shouldBe 204
    }

    "throw BadRequestException when a 400 response is returned" in new BadRequest {
      intercept[BadRequestException] {
        await(connector.updateNotifications(someStatus))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new UpstreamFailure {
      intercept[Upstream5xxResponse] {
        await(connector.updateNotifications(someStatus))
      }
    }
  }
}