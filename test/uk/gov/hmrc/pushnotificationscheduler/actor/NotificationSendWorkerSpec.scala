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
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.http.HttpException
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.actor.WorkPullingPattern.Epic
import uk.gov.hmrc.pushnotificationscheduler.domain.NotificationStatus.{Delivered, Disabled, Queued}
import uk.gov.hmrc.pushnotificationscheduler.domain.{Notification, RegistrationToken}
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services.{PushNotificationService, SnsClientService}

import scala.concurrent.Future

class NotificationSendWorkerSpec extends UnitSpec with MockitoSugar {
  type Work = Seq[RegistrationToken]

  val mockMetrics = mock[Metrics]
  val mockSnsClient = mock[SnsClientService]
  val mockPushNotification = mock[PushNotificationService]
  val notificationCaptor = ArgumentCaptor.forClass(classOf[Seq[Notification]])

  private abstract class Setup extends TestKit(ActorSystem("AkkaTestSystem")) {

    implicit val timeout = Timeout(1, MINUTES)

    val master = system.actorOf(Props[Master[Work]], "master")
    val worker = system.actorOf(NotificationSendWorker.props(master, mockSnsClient, mockPushNotification, mockMetrics))

    val someNotifications = List(
      Notification("msg-1", "end:point:a", "’TWAS in the month of December, and in the year 1883"),
      Notification("msg-2", "end:point:a", "That a monster whale came to Dundee"),
      Notification("msg-3", "end:point:a", "Resolved for a few days to sport and play"),
      Notification("msg-4", "end:point:a", "And devour the small fishes in the silvery Tay"))
    val moreNotifications = List(
      Notification("msg-5", "end:point:b", "So the monster whale did sport and play"),
      Notification("msg-6", "end:point:c", "Among the innocent little fishes in the beautiful Tay"))
    val evenMoreNotifications = List(
      Notification("msg-7", "end:point:d", "Until he was seen by some men one day"),
      Notification("msg-8", "end:point:d", "And they resolved to catch him without delay")
    )
    val unsentNotifications = List(someNotifications, moreNotifications, evenMoreNotifications)

    val someStatuses = Map("msg-1" -> Delivered, "msg-2" -> Delivered, "msg-3" -> Queued, "msg-4" -> Delivered)
    val moreStatuses = Map("msg-5" -> Delivered, "msg-6" -> Queued)
    val evenMoreStatuses = Map("msg-7" -> Disabled, "msg-8" -> Disabled)

    val epic = Epic(unsentNotifications)

    when(mockSnsClient.sendNotifications(ArgumentMatchers.eq(someNotifications))).thenReturn(Future.successful(someStatuses))
    when(mockSnsClient.sendNotifications(ArgumentMatchers.eq(moreNotifications))).thenReturn(Future.successful(moreStatuses))
    when(mockSnsClient.sendNotifications(ArgumentMatchers.eq(evenMoreNotifications))).thenReturn(Future.successful(evenMoreStatuses))

    when(mockPushNotification.updateNotifications(ArgumentMatchers.eq(someStatuses))).thenAnswer(new UpdateSuccess)
    when(mockPushNotification.updateNotifications(ArgumentMatchers.eq(evenMoreStatuses))).thenAnswer(new UpdateSuccess)
    when(mockPushNotification.updateNotifications(ArgumentMatchers.eq(moreStatuses))).thenReturn(Future.failed(new HttpException("Wibble", 500)))
  }

  "When started with an epic a NotificationSendWorker" should {
    "forward notifications to SNS" in new Setup {

      await(master ? epic)

      verify(mockSnsClient, times(3)).sendNotifications(notificationCaptor.capture())

      val firstInvocation: Seq[Notification] = notificationCaptor.getAllValues.get(0)
      val secondInvocation: Seq[Notification] = notificationCaptor.getAllValues.get(1)

      firstInvocation shouldBe someNotifications
      secondInvocation shouldBe moreNotifications

      verify(mockMetrics).incrementNotificationDelivered(ArgumentMatchers.eq(3L))
      verify(mockMetrics).incrementNotificationDisabled(ArgumentMatchers.eq(2L))
      verify(mockMetrics).incrementNotificationRequeued(ArgumentMatchers.eq(1L))
      verify(mockMetrics).incrementNotificationSendFailure(ArgumentMatchers.eq(2L))
    }
  }
}

class UpdateSuccess extends Answer[Future[_]] {
  override def answer(invocationOnMock: InvocationOnMock): Future[_] = Future.successful(Unit)
}

