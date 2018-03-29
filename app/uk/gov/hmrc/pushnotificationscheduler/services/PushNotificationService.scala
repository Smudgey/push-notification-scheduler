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

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Logger
import uk.gov.hmrc.pushnotificationscheduler.actor.CallbackResultBatch
import uk.gov.hmrc.pushnotificationscheduler.connectors.PushNotificationConnectorApi
import uk.gov.hmrc.pushnotificationscheduler.domain.{Callback, Notification, NotificationStatus}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@ImplementedBy(classOf[PushNotificationService])
trait PushNotificationServiceApi extends EntityManager {
  override val entities = "push notifications"

  def getQueuedNotifications: Future[Seq[Notification]]
  def getTimedOutNotifications: Future[Seq[Notification]]
  def updateNotifications(notificationStatus: Map[String,NotificationStatus]): Future[Unit]

  def getCallbacks(): Future[Seq[Callback]]
  def updateCallbacks(callbackOutcomes:CallbackResultBatch): Future[Boolean]
}

@Singleton
class PushNotificationService @Inject() (connector: PushNotificationConnectorApi, override val logger: Logger) extends PushNotificationServiceApi {

  override def getQueuedNotifications: Future[Seq[Notification]] = fetch[Notification](connector.getQueuedNotifications())

  override def getTimedOutNotifications: Future[Seq[Notification]] = fetch[Notification](connector.getTimedOutNotifications())

  override def updateNotifications(notificationStatus: Map[String, NotificationStatus]): Future[Unit] =
    update(connector.updateNotifications(notificationStatus))

  override def getCallbacks(): Future[Seq[Callback]] =
    fetch(connector.getUndeliveredCallbacks())

  override def updateCallbacks(callbackOutcomes:CallbackResultBatch): Future[Boolean] = {
    connector.updateCallbacks(callbackOutcomes)
  }
}

