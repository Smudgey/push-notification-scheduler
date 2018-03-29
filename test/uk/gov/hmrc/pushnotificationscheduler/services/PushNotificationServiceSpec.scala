/*
 * Copyright 2018 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.Logger
import uk.gov.hmrc.http.{HttpException, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.actor.CallbackResultBatch
import uk.gov.hmrc.pushnotificationscheduler.connectors.{Error, PushNotificationConnector, Success}
import uk.gov.hmrc.pushnotificationscheduler.domain.NotificationStatus.{Delivered, Queued}
import uk.gov.hmrc.pushnotificationscheduler.domain._
import uk.gov.hmrc.pushnotificationscheduler.support.MockAnswer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class PushNotificationServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures with MockAnswer {

  private trait Setup extends MockitoSugar {
    val connector = mock[PushNotificationConnector]
    val logger = mock[Logger]

    val service = new PushNotificationService(connector, logger)

    val someNotificationWithoutMessageId = Notification("msg-1", "end:point:a", "Twas brillig, and the slithy toves", None, "windows")
    val otherNotificationWithMessageId = Notification("msg-2", "end:point:b", "Did gyre and gimble in the wabe", Some("1"), "windows")

    val someStatuses = Map("msg-1" -> Delivered, "msg-2" -> Queued)
  }

  private trait Success extends Setup {
    when(connector.getQueuedNotifications()(any[ExecutionContext])).thenReturn(Future.successful(Seq(someNotificationWithoutMessageId, otherNotificationWithMessageId)))
    when(connector.getTimedOutNotifications()(any[ExecutionContext])).thenReturn(Future.successful(Seq(otherNotificationWithMessageId, someNotificationWithoutMessageId)))
    when(connector.updateNotifications(any[Map[String,NotificationStatus]])(any[ExecutionContext]())).thenReturn(Future.successful(Success(200)))
  }

  private trait NotFound extends Setup {
    when(connector.getQueuedNotifications()(any[ExecutionContext])).thenReturn(Future.failed(new HttpException("there's nothing for you here", 404)))
    when(connector.getTimedOutNotifications()(any[ExecutionContext])).thenReturn(Future.failed(new HttpException("there's still nothing here", 404)))
  }

  private trait Unavailable extends Setup {
    when(connector.getQueuedNotifications()(any[ExecutionContext])).thenReturn(Future.failed(new HttpException("unable to acquire lock", 503)))
    when(connector.getTimedOutNotifications()(any[ExecutionContext])).thenReturn(Future.failed(new HttpException("unable to acquire lock", 503)))
  }

  private trait BadRequest extends Setup {
    when(connector.updateNotifications(any[Map[String,NotificationStatus]])(any[ExecutionContext]())).thenReturn(Future.successful(Error(400)))
  }

  private trait Failed extends Setup {
    when(connector.getQueuedNotifications()(any[ExecutionContext])).thenReturn(Future.failed(Upstream5xxResponse("Kaboom!", 500, 500)))
    when(connector.getTimedOutNotifications()(any[ExecutionContext])).thenReturn(Future.failed(Upstream5xxResponse("Bash!", 500, 500)))
    when(connector.updateNotifications(any[Map[String,NotificationStatus]])(any[ExecutionContext])).thenReturn(Future.failed(Upstream5xxResponse("Kaboom!", 500, 500)))
  }

  private trait SuccessCallback extends Setup {

    val callback1Success = Callback("http://somehost/a", PushMessageStatus.Acknowledge.toString, CallbackResponse("messageId1", Some("Yes")), 1)
    val callback2Success = Callback("http://somehost/b", PushMessageStatus.Acknowledge.toString, CallbackResponse("messageId2", Some("Yes")), 1)
    val someCallbacks = List(callback1Success, callback2Success)

    val updatedCallbacks = CallbackResultBatch(List(
      CallbackResult("messageId1", PushMessageStatus.Acknowledge.toString, success = true),
      CallbackResult("messageId2", PushMessageStatus.Acknowledge.toString, success = true))
    )
  }

  "PushNotificationServiceSpec getQueuedNotifications" should {

    "return queued notifications when unsent notifications are available" in new Success {

      val result = await(service.getQueuedNotifications)

      result.size shouldBe 2
      result.head shouldBe someNotificationWithoutMessageId
      result(1) shouldBe otherNotificationWithMessageId
    }

    "return an empty list and log a message when no queued notifications are available" in new NotFound {

      val result = await(service.getQueuedNotifications)

      result.size shouldBe 0
    }

    "return an empty list and log a message when the push notification service is temporarily unavailable" in new Unavailable {

      val result = await(service.getQueuedNotifications)

      result.size shouldBe 0
    }

    "return an empty list and log a message when the push notification service fails" in new Failed {

      val result = await(service.getQueuedNotifications)

      result.size shouldBe 0
    }
  }

  "PushNotificationServiceSpec getTimedOutNotifications" should {

    "return timed-out notifications when such notifications are available" in new Success {

      val result = await(service.getTimedOutNotifications)

      result.size shouldBe 2
      result.head shouldBe otherNotificationWithMessageId
      result(1) shouldBe someNotificationWithoutMessageId
    }

    "return an empty list and log a message when no timed-out notifications are available" in new NotFound {

      val result = await(service.getTimedOutNotifications)

      result.size shouldBe 0
    }

    "return an empty list and log a message when the push notification service is temporarily unavailable" in new Unavailable {

      val result = await(service.getTimedOutNotifications)

      result.size shouldBe 0
    }

    "return an empty list and log a message when the push notification service fails" in new Failed {

      val result = await(service.getTimedOutNotifications)

      result.size shouldBe 0
    }
  }

  "PushNotificationService updateNotifications" should {
    "return success when it has successfully updated notification statuses" in new Success {

      val result = await(service.updateNotifications(someStatuses))

      val captor: ArgumentCaptor[Map[String,NotificationStatus]] = ArgumentCaptor.forClass(classOf[Map[String,NotificationStatus]])
      verify(connector).updateNotifications(captor.capture())(any[ExecutionContext])

      captor.getValue shouldBe someStatuses
    }

    "fail when it can when it cannot update notification statuses because of a problem with the data" in new BadRequest {
      intercept[HttpException] {
        await(service.updateNotifications(someStatuses))
      }.responseCode shouldBe 400
    }

    "fail when it cannot notification statuses because of a remote service failure" in new Failed {
      intercept[Upstream5xxResponse] {
        await(service.updateNotifications(someStatuses))
      }.reportAs shouldBe 500
    }
  }

  "PushNotificationService receive callbacks" should {

    "return list of callbacks" in new SuccessCallback {

      when(connector.getUndeliveredCallbacks()).thenAnswer(defineResult(Future.successful(someCallbacks)))
      val result = await(service.getCallbacks())
      result shouldBe someCallbacks
    }

    "return empty list when no callbacks found" in new SuccessCallback {
      when(connector.getUndeliveredCallbacks()).thenAnswer(defineResult(Future.successful(List.empty)))

      val result = await(service.getCallbacks())
      result shouldBe Seq.empty
    }

    "return empty list when connector fails" in new SuccessCallback {
      when(connector.getUndeliveredCallbacks()).thenAnswer(defineResult(Future.failed(new Exception("Controlled explosion"))))

      val result = await(service.getCallbacks())
      result shouldBe Seq.empty
    }

  }

  "PushNotificationService update callbacks" should {

    "successfully update the state of the callbacks" in new SuccessCallback {
      when(connector.updateCallbacks(updatedCallbacks)).thenAnswer(defineResult(Future.successful(true)))

      val result = await(service.updateCallbacks(updatedCallbacks))
      result shouldBe true
    }

    "return false when service does not update all records" in new SuccessCallback {
      when(connector.updateCallbacks(updatedCallbacks)).thenAnswer(defineResult(Future.successful(false)))

      val result = await(service.updateCallbacks(updatedCallbacks))
      result shouldBe false
    }

    "throw an exception when the connector results in an exception" in new SuccessCallback {
      when(connector.updateCallbacks(updatedCallbacks)).thenAnswer(defineResult(Future.failed(new Exception("Controlled explosion"))))

      await(
        service.updateCallbacks(updatedCallbacks).recover {
          case ex:Exception => ex.getMessage should be ("Controlled explosion")
      })
    }
  }

}
