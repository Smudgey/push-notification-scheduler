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

package uk.gov.hmrc.pushnotificationscheduler.domain

import play.api.libs.json._
import uk.gov.hmrc.pushnotificationscheduler.domain.NotificationStatus.{Delivered, Queued, Revoked}

trait NotificationStatus

object NotificationStatus {
  val queued = "queued"
  val sent = "sent"
  val delivered = "delivered"
  val revoked = "disabled"

  case object Queued extends NotificationStatus {
    override def toString: String = queued
  }

  case object Sent extends NotificationStatus {
    override def toString: String = sent
  }

  case object Delivered extends NotificationStatus {
    override def toString: String = delivered
  }

  case object Revoked extends NotificationStatus {
    override def toString: String = revoked
  }

  val reads: Reads[NotificationStatus] = new Reads[NotificationStatus] {
    override def reads(json: JsValue): JsResult[NotificationStatus] = json match {
      case JsString(NotificationStatus.queued) => JsSuccess(Queued)
      case JsString(NotificationStatus.sent) => JsSuccess(Sent)
      case JsString(NotificationStatus.delivered) => JsSuccess(Delivered)
      case JsString(NotificationStatus.revoked) => JsSuccess(Revoked)
      case _ => JsError(s"Failed to resolve $json")
    }
  }

  val writes: Writes[NotificationStatus] = new Writes[NotificationStatus] {
    override def writes(status: NotificationStatus): JsString = status match {
      case Queued => JsString(queued)
      case Sent => JsString(sent)
      case Delivered => JsString(delivered)
      case Revoked => JsString(revoked)
    }
  }

  implicit val formats = Format(NotificationStatus.reads, NotificationStatus.writes)
}

trait DeliveryStatus {
  def toNotificationStatus: NotificationStatus = ???
}

object DeliveryStatus extends NotificationStatus {
  val success = "success"
  val failed = "failed"
  val disabled = "disabled"

  case object Success extends DeliveryStatus {
    override def toString: String = success

    override def toNotificationStatus: NotificationStatus = Delivered
  }

  case object Failed extends DeliveryStatus {
    override def toString: String = failed

    override def toNotificationStatus: NotificationStatus = Queued
  }

  case object Disabled extends DeliveryStatus {
    override def toString: String = disabled

    override def toNotificationStatus: NotificationStatus = Revoked
  }

  implicit val reads: Reads[DeliveryStatus] = new Reads[DeliveryStatus] {
    override def reads(json: JsValue): JsResult[DeliveryStatus] = json match {
      case JsString(DeliveryStatus.success) => JsSuccess(Success)
      case JsString(DeliveryStatus.failed) => JsSuccess(Failed)
      case JsString(DeliveryStatus.disabled) => JsSuccess(Disabled)
      case _ => JsError(s"Failed to resolve $json")
    }
  }
}

case class Notification(id: String, endpointArn: String, message: String, messageId: Option[String], os: String)

object Notification {
  implicit val formats = Json.format[Notification]
}
