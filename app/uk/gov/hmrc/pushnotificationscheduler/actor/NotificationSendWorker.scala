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
import uk.gov.hmrc.pushnotificationscheduler.domain.DeliveryStatus.{Failed, Success, Disabled}
import uk.gov.hmrc.pushnotificationscheduler.domain.{DeliveryStatus, Notification}
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services.{PushNotificationService, SnsClientService}

import scala.concurrent.Future
import scala.util.Failure

class NotificationSendWorker(master: ActorRef, snsClientService: SnsClientService, pushNotificationService: PushNotificationService, metrics: Metrics) extends Worker[Seq[Notification]](master) {
  override def doWork(work: Seq[Notification]): Future[_] = {
    snsClientService.sendNotifications(work).map { (messageIdToStatusMap: Map[String, DeliveryStatus]) => {
      val messageIdToNotificationStatusMap = messageIdToStatusMap.map(kv => (kv._1, kv._2.toNotificationStatus))
      pushNotificationService.updateNotifications(messageIdToNotificationStatusMap)
    }.onComplete {
        case Failure(e) =>
          Logger.error(s"Failed to update notification status: ${e.getMessage}")
          metrics.incrementNotificationSendFailure(work.size)
          Future.failed(e)
        case _ =>
          metrics.incrementNotificationDelivered(messageIdToStatusMap.count(_._2 == Success))
          metrics.incrementNotificationRequeued(messageIdToStatusMap.count(_._2 == Failed))
          metrics.incrementNotificationDisabled(messageIdToStatusMap.count(_._2 == Disabled))
      }
    }
  }
}

object NotificationSendWorker {
  def props(master: ActorRef, snsClientService: SnsClientService, pushNotificationService: PushNotificationService, metrics: Metrics): Props =
    Props(new NotificationSendWorker(master, snsClientService, pushNotificationService, metrics))
}