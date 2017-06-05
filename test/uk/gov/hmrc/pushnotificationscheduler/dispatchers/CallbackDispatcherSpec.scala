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

package uk.gov.hmrc.pushnotificationscheduler.dispatchers

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.mockito.Mockito._
import org.mockito.{Mockito, ArgumentCaptor, ArgumentMatchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.actor.CallbackResultBatch
import uk.gov.hmrc.pushnotificationscheduler.connectors.{Success, ReplyToClientConnector}
import uk.gov.hmrc.pushnotificationscheduler.domain.{Callback, CallbackResult, CallbackResponse, PushMessageStatus}
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services._
import uk.gov.hmrc.pushnotificationscheduler.support.MockAnswer

import scala.concurrent.Future
import scala.concurrent.Future._

class CallbackDispatcherSpec extends UnitSpec with ScalaFutures with MockitoSugar with OneAppPerSuite with MockAnswer {
  val mockMetrics = mock[Metrics]
  val mockPushNotification = mock[PushNotificationService]
  val mockReplyToClient = mock[ReplyToClientConnector]

  private abstract class Setup extends TestKit(ActorSystem("AkkaTestSystem")) {
    val callback1Success = Callback("http://somehost/a", PushMessageStatus.Acknowledge.toString, CallbackResponse("messageId1", Some("Yes")), 1)
    val callback2Success = Callback("http://somehost/b", PushMessageStatus.Acknowledge.toString, CallbackResponse("messageId2", Some("Yes")), 1)
    val someCallbacks = List(callback1Success, callback2Success)

    val updatedCallbacks = CallbackResultBatch(List(
      CallbackResult("messageId1", PushMessageStatus.Acknowledge.toString, success = true),
      CallbackResult("messageId2", PushMessageStatus.Acknowledge.toString, success = true))
    )
    val emptyCallbacks= List.empty

    val notificationCaptor = ArgumentCaptor.forClass(classOf[CallbackResultBatch])
    val replyToClientCaptor = ArgumentCaptor.forClass(classOf[Callback])

    val dispatcher = new CallbackDispatcher(4, mockPushNotification, mockReplyToClient, system, mockMetrics)
  }

  "scheduling the Notification dispatcher" should {
    "process queued and timed-out notifications" in new Setup {

      when(mockPushNotification.getCallbacks()).thenAnswer(defineResult(Future.successful(someCallbacks)))
      when(mockPushNotification.updateCallbacks(updatedCallbacks)).thenAnswer(defineResult(Future.successful(true)))

      when(mockReplyToClient.replyToClient(callback1Success)).thenAnswer(defineResult(Future.successful(Success(200))))
      when(mockReplyToClient.replyToClient(callback2Success)).thenAnswer(defineResult(Future.successful(Success(200))))

      await(dispatcher.processCallbacks())

      Eventually.eventually {
        verify(mockReplyToClient, times(2)).replyToClient(replyToClientCaptor.capture())
        val firstClientInvocation:Callback = replyToClientCaptor.getAllValues.get(0)
        val secondClientInvocation:Callback = replyToClientCaptor.getAllValues.get(1)
        firstClientInvocation shouldBe callback1Success
        secondClientInvocation shouldBe callback2Success

        verify(mockPushNotification).updateCallbacks(notificationCaptor.capture())
        val firstInvocation:CallbackResultBatch = notificationCaptor.getAllValues.get(0)
        firstInvocation shouldBe updatedCallbacks

        verify(mockMetrics).incrementCallbackSuccess(ArgumentMatchers.eq(2L))
      }
    }

    "do nothing when there are no callbacks" in new Setup {
      reset(mockReplyToClient)

      when(mockPushNotification.getCallbacks()).thenAnswer(defineResult(Future.successful(emptyCallbacks)))
      when(mockReplyToClient.replyToClient(ArgumentMatchers.any[Callback]())).thenReturn(failed(new Exception("should not be called")))

      await(dispatcher.processCallbacks())

      Eventually.eventually(
        Mockito.verify(mockReplyToClient, times(0)).replyToClient(ArgumentMatchers.any[Callback]())
      )
    }
  }
}
