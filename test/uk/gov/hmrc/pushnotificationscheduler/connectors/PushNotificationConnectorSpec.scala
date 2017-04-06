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
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotificationscheduler.domain.NotificationStatus.{Delivered, Queued}
import uk.gov.hmrc.pushnotificationscheduler.domain.{Notification, NotificationStatus}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class PushNotificationConnectorSpec extends UnitSpec with WithFakeApplication with ServicesConfig with ScalaFutures {

  private trait Setup extends MockitoSugar {
    val mockHttp: WSHttp = mock[WSHttp]

    val connector = new PushNotificationConnector("http://somewhere:8080", mockHttp)

    val someNotification = Notification("msg-1", "end:point:1", "hello world")
    val otherNotification = Notification("msg-2", "end:point:2", "goodbye")

    val unsentNotifications = Seq(someNotification, otherNotification)

    val someStatus = Map("msg-1" -> Delivered, "msg-2" -> Queued)
  }

  private trait Success extends Setup {
    doReturn(successful(unsentNotifications), Nil: _*).when(mockHttp).GET[Seq[Notification]](matches(s"${connector.serviceUrl}/notifications/unsent"), any[Seq[(String, String)]]())(any[HttpReads[Seq[Notification]]](), any[HeaderCarrier]())
    doReturn(successful(HttpResponse(204, None)), Nil: _*).when(mockHttp).POST[Map[String, NotificationStatus], HttpResponse](matches(s"${connector.serviceUrl}/notifications/status"), any[Map[String, NotificationStatus]](), any[Seq[(String, String)]])(any[Writes[Map[String, NotificationStatus]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())

  }

  private trait BadRequest extends Setup {
    doReturn(failed(new BadRequestException("SPLAT!")), Nil: _*).when(mockHttp).GET[Seq[Notification]](matches(s"${connector.serviceUrl}/notifications/unsent"), any[Seq[(String, String)]]())(any[HttpReads[Seq[Notification]]](), any[HeaderCarrier]())
    doReturn(failed(new BadRequestException("CRASH!")), Nil: _*).when(mockHttp).POST[Map[String, NotificationStatus], HttpResponse](matches(s"${connector.serviceUrl}/notifications/status"), any[Map[String, NotificationStatus]](), any[Seq[(String, String)]])(any[Writes[Map[String, NotificationStatus]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())
  }

  private trait UpstreamFailure extends Setup {
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).GET[Seq[Notification]](matches(s"${connector.serviceUrl}/notifications/unsent"), any[Seq[(String, String)]]())(any[HttpReads[Seq[Notification]]](), any[HeaderCarrier]())
    doReturn(failed(Upstream5xxResponse("BOOM!", 500, 500)), Nil: _*).when(mockHttp).POST[Map[String, NotificationStatus], HttpResponse](matches(s"${connector.serviceUrl}/notifications/status"), any[Map[String, NotificationStatus]](), any[Seq[(String, String)]])(any[Writes[Map[String, NotificationStatus]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())
  }

  "getUnsentNotifications" should {
    "return unsent notifications when a 200 response is received with a valid json payload" in new Success {
      val result: Seq[Notification] = await(connector.getUnsentNotifications())

      result.size shouldBe 2
      result.head shouldBe someNotification
      result(1) shouldBe otherNotification
    }

    "throw BadRequestException when a 400 response is returned" in new BadRequest {
      intercept[BadRequestException] {
        await(connector.getUnsentNotifications())
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new UpstreamFailure {
      intercept[Upstream5xxResponse] {
        await(connector.getUnsentNotifications())
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