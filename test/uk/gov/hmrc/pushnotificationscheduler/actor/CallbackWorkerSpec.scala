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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.actor.WorkPullingPattern.Epic
import uk.gov.hmrc.pushnotificationscheduler.connectors.{ReplyToClientConnector, Success}
import uk.gov.hmrc.pushnotificationscheduler.domain.{Callback, CallbackResponse, CallbackResult, PushMessageStatus}
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services._
import uk.gov.hmrc.pushnotificationscheduler.support.MockAnswer

import scala.concurrent.Future

class CallbackWorkerSpec extends UnitSpec with MockitoSugar with MockAnswer {
  type Work = Seq[Callback]

  private abstract class Setup extends TestKit(ActorSystem("AkkaTestSystemCallback")) {

    val notificationCaptor = ArgumentCaptor.forClass(classOf[CallbackResultBatch])
    val replyToClientCaptor = ArgumentCaptor.forClass(classOf[Callback])

    val mockMetrics = mock[Metrics]
    val mockPushNotification = mock[PushNotificationService]
    val mockReplyToClient = mock[ReplyToClientConnector]

    implicit val timeout = Timeout(1, MINUTES)

    val master: ActorRef = system.actorOf(Props[Master[Work]], "master")
    val worker: ActorRef = system.actorOf(CallbackWorker.props(master, mockPushNotification, mockReplyToClient, mockMetrics))

    val callback1Success = Callback("http://somehost/a", PushMessageStatus.Acknowledge.toString, CallbackResponse("messageId1", Some("Yes")), 1)
    val callback2Success = Callback("http://somehost/b", PushMessageStatus.Acknowledge.toString, CallbackResponse("messageId2", Some("Yes")), 1)
    val someCallbacks = List(callback1Success, callback2Success)
    val epic: Epic[List[Callback]] = Epic(List(someCallbacks))


    def verifyClientCallbacks(updatedCallbacks: CallbackResultBatch) = {
      verify(mockReplyToClient, times(2)).replyToClient(replyToClientCaptor.capture())
      val firstClientInvocation:Callback = replyToClientCaptor.getAllValues.get(0)
      val secondClientInvocation:Callback = replyToClientCaptor.getAllValues.get(1)
      firstClientInvocation shouldBe callback1Success
      secondClientInvocation shouldBe callback2Success

      verify(mockPushNotification).updateCallbacks(notificationCaptor.capture())
      val firstInvocation:CallbackResultBatch = notificationCaptor.getAllValues.get(0)
      firstInvocation shouldBe updatedCallbacks
    }

  }

  private abstract class Success extends Setup {
    val updatedCallbacks: CallbackResultBatch = CallbackResultBatch(List(
      CallbackResult("messageId1", PushMessageStatus.Acknowledge.toString, success = true),
      CallbackResult("messageId2", PushMessageStatus.Acknowledge.toString, success = true))
    )

    when(mockPushNotification.getCallbacks()).thenAnswer(defineResult(Future.successful(someCallbacks)))
    when(mockPushNotification.updateCallbacks(updatedCallbacks)).thenAnswer(defineResult(Future.successful(true)))

    when(mockReplyToClient.replyToClient(callback1Success)).thenAnswer(defineResult(Future.successful(Success(200))))
    when(mockReplyToClient.replyToClient(callback2Success)).thenAnswer(defineResult(Future.successful(Success(200))))
  }

  private abstract class SetupClientReplyFailure extends Setup {

    val updatedCallbacks = CallbackResultBatch(List(
      CallbackResult("messageId1", PushMessageStatus.Acknowledge.toString, success = false),
      CallbackResult("messageId2", PushMessageStatus.Acknowledge.toString, success = false))
    )

    when(mockPushNotification.getCallbacks()).thenAnswer(defineResult(Future.successful(someCallbacks)))
    when(mockPushNotification.updateCallbacks(updatedCallbacks)).thenAnswer(defineResult(Future.successful(true)))

    when(mockReplyToClient.replyToClient(callback1Success)).thenAnswer(defineResult(Future.successful(Success(500))))
    when(mockReplyToClient.replyToClient(callback2Success)).thenAnswer(defineResult(Future.successful(Success(500))))
  }

  private abstract class SetupClientReplySuccessAndFailure extends Setup {

    val updatedCallbacks: CallbackResultBatch = CallbackResultBatch(List(
      CallbackResult("messageId1", PushMessageStatus.Acknowledge.toString, success = false),
      CallbackResult("messageId2", PushMessageStatus.Acknowledge.toString, success = true))
    )

    when(mockPushNotification.getCallbacks()).thenAnswer(defineResult(Future.successful(someCallbacks)))
    when(mockPushNotification.updateCallbacks(updatedCallbacks)).thenAnswer(defineResult(Future.successful(true)))

    when(mockReplyToClient.replyToClient(callback1Success)).thenAnswer(defineResult(Future.successful(Success(500))))
    when(mockReplyToClient.replyToClient(callback2Success)).thenAnswer(defineResult(Future.successful(Success(200))))
  }

  private abstract class NotificationUpdateFailure extends Setup {
    val updatedCallbacks: CallbackResultBatch = CallbackResultBatch(List(
      CallbackResult("messageId1", PushMessageStatus.Acknowledge.toString, success = true),
      CallbackResult("messageId2", PushMessageStatus.Acknowledge.toString, success = true))
    )

    when(mockPushNotification.getCallbacks()).thenAnswer(defineResult(Future.successful(someCallbacks)))
    when(mockPushNotification.updateCallbacks(updatedCallbacks)).thenAnswer(defineResult(Future.failed(new Exception("Controlled explosion"))))

    when(mockReplyToClient.replyToClient(callback1Success)).thenAnswer(defineResult(Future.successful(Success(200))))
    when(mockReplyToClient.replyToClient(callback2Success)).thenAnswer(defineResult(Future.successful(Success(200))))
  }


  "When started with an epic a CallbackWorker" should {

    "successfully callback clients with state change of message and update push-notification with outcome" in new Success {

      await(master ? epic)

      verifyClientCallbacks(updatedCallbacks)

      verify(mockMetrics).incrementCallbackSuccess(ArgumentMatchers.eq(2L))
    }

    "successfully callback clients but client fails with error and update push-notification with outcome" in new SetupClientReplyFailure {

      await(master ? epic)

      verifyClientCallbacks(updatedCallbacks)

      verify(mockMetrics).incrementCallbackFailure(ArgumentMatchers.eq(2L))
    }

    "successfully callback clients with mix of client success/fail and update push-notification with outcome" in new SetupClientReplySuccessAndFailure {

      await(master ? epic)

      verifyClientCallbacks(updatedCallbacks)

      verify(mockMetrics).incrementCallbackSuccess(ArgumentMatchers.eq(1L))
      verify(mockMetrics).incrementCallbackFailure(ArgumentMatchers.eq(1L))
    }

    "successfully callback clients and push-notification fails updating state" in new NotificationUpdateFailure {

      await(master ? epic)

      verifyClientCallbacks(updatedCallbacks)

      verify(mockMetrics).incrementNotificationCallbackUpdateFailure(ArgumentMatchers.eq(1L))
    }

  }

}

