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
import play.api.libs.json._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HttpDelete, HttpGet, HttpPost, HttpReads}
import uk.gov.hmrc.pushnotificationscheduler.config.ServicesCircuitBreaker
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SnsClientConnector])
trait SnsClientConnectorApi extends GenericConnector with ServicesConfig with ServicesCircuitBreaker {
  this: ServicesCircuitBreaker =>

  override val externalServiceName: String = "sns-client"

  implicit object OptionStringReads extends Reads[Option[String]] {
    def reads(json: JsValue) = json match {
      case JsString(s) => JsSuccess(Option(s))
      case JsNull => JsSuccess(None)
      case _ => JsError("expected Option[String]")
    }
  }

  def exchangeTokens(tokens: Seq[RegistrationToken])(implicit r: HttpReads[Map[String,String]], ex: ExecutionContext): Future[Map[String,Option[String]]] = {
    submit[Seq[RegistrationToken], Map[String,Option[String]]]("/endpoints", tokens)
  }
}

@Singleton
class SnsClientConnector @Inject() (@Named("snsClientUrl") val serviceUrl: String, val http: HttpGet with HttpPost with HttpDelete) extends SnsClientConnectorApi with ServicesConfig with ServicesCircuitBreaker