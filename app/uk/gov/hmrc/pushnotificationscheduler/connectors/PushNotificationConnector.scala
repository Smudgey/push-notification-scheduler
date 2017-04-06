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
import uk.gov.hmrc.pushnotificationscheduler.domain.{Notification, NotificationStatus}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PushNotificationConnector])
trait PushNotificationConnectorApi extends GenericConnector {
  override val externalServiceName: String = "push-notification"

  def getUnsentNotifications(maxBatchSize: Int = defaultBatchSize)(implicit ex: ExecutionContext): Future[Seq[Notification]]
  def updateNotifications(notificationStatus: Map[String,NotificationStatus])(implicit ex: ExecutionContext): Future[Response]
}

@Singleton
class PushNotificationConnector @Inject()(@Named("pushNotificationUrl") val serviceUrl: String, val http: HttpGet with HttpPost with HttpDelete) extends PushNotificationConnectorApi {
  override def getUnsentNotifications(maxBatchSize: Int = defaultBatchSize)(implicit ex: ExecutionContext): Future[Seq[Notification]] = {
    get[Seq[Notification]]("/notifications/unsent", List(("maxBatchSize", maxBatchSize.toString)))
  }

  override def updateNotifications(notificationStatus: Map[String,NotificationStatus])(implicit ex: ExecutionContext): Future[Response] = {
    post[Map[String, NotificationStatus]]("/notifications/status", notificationStatus)
  }
}
