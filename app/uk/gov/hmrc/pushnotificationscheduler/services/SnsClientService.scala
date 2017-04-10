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
import uk.gov.hmrc.pushnotificationscheduler.connectors.SnsClientConnectorApi
import uk.gov.hmrc.pushnotificationscheduler.domain.{Notification, NotificationStatus, RegistrationToken}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[SnsClientService])
trait SnsClientServiceApi {

  def exchangeTokens(tokens: Seq[RegistrationToken]): Future[Map[String,Option[String]]]
  def sendNotifications(notifications: Seq[Notification]): Future[Map[String, NotificationStatus]]
}

@Singleton
class SnsClientService @Inject() (connector: SnsClientConnectorApi) extends SnsClientServiceApi {
  override def exchangeTokens(tokens: Seq[RegistrationToken]): Future[Map[String, Option[String]]] = {
    connector.exchangeTokens(tokens).recover{
      case e: Throwable =>
        Logger.error(s"failed to exchange tokens: ${e.getMessage}", e)
        Map.empty
    }
  }

  override def sendNotifications(notifications: Seq[Notification]): Future[Map[String, NotificationStatus]] = {
    connector.sendNotifications(notifications).recover{
      case e: Throwable =>
        Logger.error(s"failed to send notifications: ${e.getMessage}", e)
        Map.empty
    }
  }
}
