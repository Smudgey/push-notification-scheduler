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

import java.util.concurrent.TimeUnit.HOURS
import javax.inject.{Inject, Named, Singleton}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.ImplementedBy
import uk.gov.hmrc.pushnotificationscheduler.actor.{Master, NotificationSendWorker}
import uk.gov.hmrc.pushnotificationscheduler.actor.WorkPullingPattern.{Batch, Epic, RegisterWorker}
import uk.gov.hmrc.pushnotificationscheduler.domain.Notification
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services.{PushNotificationService, SnsClientService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[NotificationDispatcher])
trait NotificationDispatcherApi {
  def processNotifications(): Future[Unit]

  def isRunning: Future[Boolean]
}

@Singleton
class NotificationDispatcher @Inject()(@Named("notificationDispatcherCount") notificationDispatcherCount: Int, snsClientService: SnsClientService, pushNotificationService: PushNotificationService, system: ActorSystem, metrics: Metrics) extends NotificationDispatcherApi {
  implicit val timeout = Timeout(1, HOURS)

  lazy val gangMaster: ActorRef = system.actorOf(Props[Master[Batch[Notification]]])

  val name = "registration-token-dispatcher"

  (1 until notificationDispatcherCount).foreach { _ =>
    gangMaster ! RegisterWorker(system.actorOf(NotificationSendWorker.props(gangMaster, snsClientService, pushNotificationService, metrics)))
  }

  override def processNotifications(): Future[Unit] = {
    for {
      notDelivered <- pushNotificationService.getUnsentNotifications
      work: List[Batch[Notification]] <- Future.successful{ if (notDelivered.nonEmpty) List(notDelivered) else List.empty }
      _ <- gangMaster ? Epic[Batch[Notification]](work)
    } yield ()
    Future.successful(Unit)
  }

  override def isRunning: Future[Boolean] = Future.successful(false)
}
