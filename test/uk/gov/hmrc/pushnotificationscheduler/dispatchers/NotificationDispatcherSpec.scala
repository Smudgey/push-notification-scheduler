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
import org.mockito.Mockito.when
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.actor.UpdateSuccess
import uk.gov.hmrc.pushnotificationscheduler.domain.NotificationStatus.{Delivered, Disabled, Queued}
import uk.gov.hmrc.pushnotificationscheduler.domain.{Notification, NotificationStatus, RegistrationToken}
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services.{PushNotificationService, SnsClientService}

import scala.concurrent.Future.successful

class NotificationDispatcherSpec extends UnitSpec with ScalaFutures with MockitoSugar with OneAppPerSuite {
  val mockMetrics = mock[Metrics]
  val mockSnsClient = mock[SnsClientService]
  val mockPushNotification = mock[PushNotificationService]

  val notificationCaptor = ArgumentCaptor.forClass(classOf[Seq[Notification]])
  val mapCaptor = ArgumentCaptor.forClass(classOf[Map[String, NotificationStatus]])

  private abstract class Setup extends TestKit(ActorSystem("AkkaTestSystem")) {
    val unsentNotifications = List(
      Notification("msg-100", "end:point:a", "I wandered lonely as a cloud"),
      Notification("msg-101", "end:point:a", "That floats on high o'er vales and hills,"),
      Notification("msg-102", "end:point:b", "When all at once I saw a crowd"),
      Notification("msg-102", "end:point:c", "A host, of golden daffodils"))
    val statuses = Map(
      "msg-100" -> Delivered,
      "msg-100" -> Delivered,
      "msg-100" -> Queued,
      "msg-100" -> Disabled)

    val dispatcher = new NotificationDispatcher(4, mockSnsClient, mockPushNotification, system, mockMetrics)
  }

  "scheduling the Notification dispatcher" should {
    "process unsent notifications" in new Setup {
      when(mockPushNotification.getUnsentNotifications).thenReturn(successful(unsentNotifications))
      when(mockPushNotification.updateNotifications(ArgumentMatchers.any[Map[String, NotificationStatus]]())).thenAnswer(new UpdateSuccess)
      when(mockSnsClient.sendNotifications(ArgumentMatchers.any[Seq[Notification]]())).thenReturn(successful(statuses))

      await(dispatcher.processNotifications())

      Eventually.eventually {
        Mockito.verify(mockSnsClient).sendNotifications(notificationCaptor.capture())
        Mockito.verify(mockPushNotification).updateNotifications(mapCaptor.capture())

        val actualNotifications: Seq[Notification] = notificationCaptor.getValue
        val actualMap: Map[String, NotificationStatus] = mapCaptor.getValue

        actualNotifications shouldBe unsentNotifications
        actualMap shouldBe statuses
      }
    }

    "do nothing when there are no unsent notification" in new Setup {
      when(mockPushNotification.getUnsentNotifications).thenReturn(successful(Seq.empty))

      await(dispatcher.processNotifications())

      Mockito.verifyZeroInteractions(mockSnsClient)
    }
  }
}