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

import akka.actor.{ActorRef, Props}
import play.api.Logger
import uk.gov.hmrc.pushnotificationscheduler.actor.WorkPullingPattern.Batch
import uk.gov.hmrc.pushnotificationscheduler.domain.DeliveryStatus.{Disabled, Failed, Success}
import uk.gov.hmrc.pushnotificationscheduler.domain.Notification
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services.{PushNotificationService, SnsClientService}

import scala.concurrent.Future

class NotificationSendWorker(master: ActorRef, snsClientService: SnsClientService, pushNotificationService: PushNotificationService, metrics: Metrics) extends Worker[Batch[Notification]](master) {
  override def doWork(work: Batch[Notification]): Future[Any] = {
    snsClientService.sendNotifications(work).map { idToDeliveryStatusMap =>
      val idToNotificationStatusMap = idToDeliveryStatusMap.map { case (id, deliveryStatus) =>
        (id, deliveryStatus.toNotificationStatus)
      }
      pushNotificationService.updateNotifications(idToNotificationStatusMap).map { _ =>
      metrics.incrementNotificationDelivered(idToDeliveryStatusMap.count(_._2 == Success))
      metrics.incrementNotificationRequeued(idToDeliveryStatusMap.count(_._2 == Failed))
      metrics.incrementNotificationDisabled(idToDeliveryStatusMap.count(_._2 == Disabled))
      }.recover { case e =>
        Logger.error(s"Failed to update notification status: ${e.getMessage}")
        metrics.incrementNotificationUpdateFailure(work.size)
      }
    }.recover { case e =>
      Logger.error(s"Failed to send notifications: ${e.getMessage}")
      metrics.incrementNotificationSendFailure(work.size)
    }
  }
}

object NotificationSendWorker {
  def props(master: ActorRef, snsClientService: SnsClientService, pushNotificationService: PushNotificationService, metrics: Metrics): Props =
    Props(new NotificationSendWorker(master, snsClientService, pushNotificationService, metrics))
}