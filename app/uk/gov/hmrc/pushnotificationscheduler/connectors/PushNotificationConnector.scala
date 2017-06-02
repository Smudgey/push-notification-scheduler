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

package uk.gov.hmrc.pushnotificationscheduler.connectors

import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import uk.gov.hmrc.play.http.{HttpDelete, HttpGet, HttpPost}
import uk.gov.hmrc.pushnotificationscheduler.actor.{CallbackBatch, CallbackResultBatch}
import uk.gov.hmrc.pushnotificationscheduler.domain.{Callback, Notification, NotificationStatus}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global


@ImplementedBy(classOf[PushNotificationConnector])
trait PushNotificationConnectorApi extends GenericConnector {
  override val externalServiceName: String = "push-notification"

  def getQueuedNotifications()(implicit ex: ExecutionContext): Future[Seq[Notification]]
  def getTimedOutNotifications()(implicit ex: ExecutionContext): Future[Seq[Notification]]
  def updateNotifications(notificationStatus: Map[String,NotificationStatus])(implicit ex: ExecutionContext): Future[Response]

  def getUndeliveredCallbacks()(implicit ex: ExecutionContext): Future[Seq[Callback]]
  def updateCallbacks(batch:CallbackResultBatch):Future[Boolean]
}

@Singleton
class PushNotificationConnector @Inject()(@Named("pushNotificationUrl") val serviceUrl: String, val http: HttpGet with HttpPost with HttpDelete) extends PushNotificationConnectorApi {
  override def getQueuedNotifications()(implicit ex: ExecutionContext): Future[Seq[Notification]] = {
    get[Seq[Notification]]("/notifications/unsent", List.empty[(String, String)])
  }

  override def getTimedOutNotifications()(implicit ex: ExecutionContext): Future[Seq[Notification]] = {
    get[Seq[Notification]]("/notifications/timedout", List.empty[(String, String)])
  }

  override def updateNotifications(notificationStatus: Map[String,NotificationStatus])(implicit ex: ExecutionContext): Future[Response] = {
    post[Map[String, NotificationStatus]]("/notifications/status", notificationStatus)
  }

  override def getUndeliveredCallbacks()(implicit ex: ExecutionContext): Future[Seq[Callback]] =
    get[CallbackBatch]("/callbacks/undelivered", List.empty[(String, String)]).map(_.batch)

  override def updateCallbacks(batch: CallbackResultBatch): Future[Boolean] =
    // 204 all updated, 202 not all updated.
    post[CallbackResultBatch]("/callbacks/status ", batch).map(resp => if (resp.status == 204) true else false)
}
