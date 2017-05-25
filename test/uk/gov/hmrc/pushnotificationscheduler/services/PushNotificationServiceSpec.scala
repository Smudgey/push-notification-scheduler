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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.mockito.ArgumentCaptor
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.Logger
import uk.gov.hmrc.play.http.{HttpException, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.connectors.{Error, PushNotificationConnector, Success}
import uk.gov.hmrc.pushnotificationscheduler.domain.NotificationStatus.{Delivered, Queued}
import uk.gov.hmrc.pushnotificationscheduler.domain.{Notification, NotificationStatus}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

class PushNotificationServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  private trait Setup extends MockitoSugar {
    val connector = mock[PushNotificationConnector]
    val logger = mock[Logger]

    val service = new PushNotificationService(connector, logger)

    val someNotificationWithoutMessageId = Notification("msg-1", "end:point:a", "Twas brillig, and the slithy toves", None, "windows")
    val otherNotificationWithMessageId = Notification("msg-2", "end:point:b", "Did gyre and gimble in the wabe", Some("1"), "windows")

    val someStatuses = Map("msg-1" -> Delivered, "msg-2" -> Queued)
  }

  private trait Success extends Setup {
    when(connector.getQueuedNotifications()(any[ExecutionContext]())).thenReturn(Future.successful(Seq(someNotificationWithoutMessageId, otherNotificationWithMessageId)))
    when(connector.updateNotifications(any[Map[String,NotificationStatus]])(any[ExecutionContext]())).thenReturn(Future.successful(Success(200)))
  }

  private trait NotFound extends Setup {
    when(connector.getQueuedNotifications()(any[ExecutionContext]())).thenReturn(Future.failed(new HttpException("there's nothing for you here", 404)))
  }

  private trait Unavailable extends Setup {
    when(connector.getQueuedNotifications()(any[ExecutionContext]())).thenReturn(Future.failed(new HttpException("unable to acquire lock", 503)))
  }

  private trait BadRequest extends Setup {
    when(connector.updateNotifications(any[Map[String,NotificationStatus]])(any[ExecutionContext]())).thenReturn(Future.successful(Error(400)))
  }

  private trait Failed extends Setup {
    when(connector.getQueuedNotifications()(any[ExecutionContext]())).thenReturn(Future.failed(Upstream5xxResponse("Kaboom!", 500, 500)))
    when(connector.updateNotifications(any[Map[String,NotificationStatus]])(any[ExecutionContext]())).thenReturn(Future.failed(Upstream5xxResponse("Kaboom!", 500, 500)))
  }

  "PushNotificationServiceSpec getUnsentNotifications" should {

    "return unsent notifications when unsent notifications are available" in new Success {

      val result = await(service.getUnsentNotifications)

      result.size shouldBe 2
      result.head shouldBe someNotificationWithoutMessageId
      result(1) shouldBe otherNotificationWithMessageId
    }

    "return an empty list and log a message when no unsent notifications are available" in new NotFound {

      val result = await(service.getUnsentNotifications)

      result.size shouldBe 0
    }

    "return an empty list and log a message when the push notification service is temporarily unavailable" in new Unavailable {

      val result = await(service.getUnsentNotifications)

      result.size shouldBe 0
    }

    "return an empty list and log a message when the push notification service fails" in new Failed {

      val result = await(service.getUnsentNotifications)

      result.size shouldBe 0
    }
  }

  "PushRegistrationService updateNotifications" should {
    "return success when it has successfully updated notification statuses" in new Success {

      val result = await(service.updateNotifications(someStatuses))

      val captor: ArgumentCaptor[Map[String,NotificationStatus]] = ArgumentCaptor.forClass(classOf[Map[String,NotificationStatus]])
      verify(connector).updateNotifications(captor.capture())(any[ExecutionContext]())

      captor.getValue shouldBe someStatuses

      result.onComplete{
        case Failure(_) => fail("should have succeeded")
        case _ => // all good
      }
    }

    "return failure when it can when it cannot update notification statuses because of a problem with the data" in new BadRequest {

      val result = await(service.updateNotifications(someStatuses))

      result.onComplete{
        case Failure(e) => e.getMessage should contain ("400")
        case _ => fail("should not have succeeded")
      }
    }

    "return failure when it cannot notification statuses because of a remote service failure" in new Failed {

      val result = await(service.updateNotifications(someStatuses))

      result.onComplete{
        case Failure(e) => e.getMessage should contain ("500")
        case _ => fail("should not have succeeded")
      }
    }
  }
}
