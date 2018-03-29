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

package uk.gov.hmrc.pushnotificationscheduler.connectors

import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import play.api.libs.json._
import uk.gov.hmrc.http.{CoreDelete, CoreGet, CorePost, HttpReads}
import uk.gov.hmrc.pushnotificationscheduler.domain.{DeliveryStatus, Notification, RegistrationToken}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SnsClientConnector])
trait SnsClientConnectorApi extends GenericConnector {

  override val externalServiceName: String = "sns-client"

  implicit object OptionStringReads extends Reads[Option[String]] {
    def reads(json: JsValue) = json match {
      case JsString(s) => JsSuccess(Option(s))
      case JsNull => JsSuccess(None)
      case _ => JsError("expected Option[String]")
    }
  }

  def exchangeTokens(tokens: Seq[RegistrationToken])(implicit r: HttpReads[Map[String, String]], ex: ExecutionContext): Future[Map[String, Option[String]]] = {
    submit[Seq[RegistrationToken], Map[String, Option[String]]]("/sns-client/endpoints", tokens)
  }

  def sendNotifications(notifications: Seq[Notification])(implicit r: HttpReads[Map[String, DeliveryStatus]], ex: ExecutionContext): Future[Map[String, DeliveryStatus]] = {
    submit[Seq[Notification], Map[String, DeliveryStatus]]("/sns-client/notifications", notifications)
  }
}

@Singleton
class SnsClientConnector @Inject()(@Named("snsClientUrl") val serviceUrl: String, val http: CoreGet with CorePost with CoreDelete) extends SnsClientConnectorApi