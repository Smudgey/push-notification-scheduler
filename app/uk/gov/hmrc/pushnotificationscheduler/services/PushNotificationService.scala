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

package uk.gov.hmrc.pushnotificationscheduler.services

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Logger
import uk.gov.hmrc.play.http.HttpException
import uk.gov.hmrc.pushnotificationscheduler.connectors.{PushNotificationConnectorApi, Response, Success}
import uk.gov.hmrc.pushnotificationscheduler.domain.{Notification, NotificationStatus}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[PushNotificationService])
trait PushNotificationServiceApi extends NotificationService {

  def getUnsentNotifications: Future[Seq[Notification]]
  def updateNotifications(notificationStatus: Map[String,NotificationStatus]): Future[_]
}

@Singleton
class PushNotificationService @Inject() (connector: PushNotificationConnectorApi) extends PushNotificationServiceApi {
  override def getUnsentNotifications: Future[Seq[Notification]] = fetch[Notification](connector.getUnsentNotifications())

  override def updateNotifications(notificationStatus: Map[String, NotificationStatus]): Future[_] =
    update(connector.updateNotifications(notificationStatus))
}

trait NotificationService {
  def fetch[T](func: => Future[Seq[T]]) = {
    func recover {
      case e: HttpException if e.responseCode == 404 =>
        Logger.info(s"no data found")
        Seq.empty
      case e: Throwable =>
        Logger.error(s"Failed to fetch data: ${e.getMessage}", e)
        Seq.empty
    }
  }

  def update(func: => Future[Response]): Future[_] = {
    func.map{ r: Response => r match {
      case _: Success =>
        Future.successful(Unit)
      case _ =>
        Logger.error(s"Update failed, status = ${r.status}")
        Future.failed(new HttpException(s"Update failed", r.status))
    }}.recover {
      case e: Throwable =>
        Logger.error(s"Update failed: ${e.getMessage}")
        Future.failed(e)
    }
  }
}