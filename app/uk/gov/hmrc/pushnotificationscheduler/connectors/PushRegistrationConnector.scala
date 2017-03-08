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

import play.api.libs.json.Writes
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.pushnotificationscheduler.config.ServicesCircuitBreaker
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken

import scala.concurrent.{ExecutionContext, Future}

sealed trait Response {
  def status: Int
}
case class Success(status:Int) extends Response
case class Error(status:Int) extends Response

trait PushRegistrationConnector {
  this: ServicesCircuitBreaker =>

  val defaultBatchSize = 10

  val externalServiceName = "push-registration"

  def http: HttpGet with HttpPost with HttpDelete

  def serviceUrl: String

  def url(path: String) = s"$serviceUrl$path"

  def getUnregisteredTokens(maxBatchSize: Int = defaultBatchSize)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Seq[RegistrationToken]] = {
    doGet(List(("maxBatchSize", maxBatchSize.toString)))
  }

  def recoverFailedRegistrations(maxBatchSize: Int = defaultBatchSize)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Seq[RegistrationToken]] = {
    doGet(List(("mode", "recover"), ("maxBatchSize", maxBatchSize.toString)))
  }

  def registerTokens(tokenToArnMap: Map[String,String])(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Response] = {
    doPost[Map[String, String]]("/registrations", tokenToArnMap)
  }

  def removeDisabledTokens(tokens: Seq[RegistrationToken])(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Response] = {
    doPost[Seq[RegistrationToken]]("/registrations/delete", tokens)
  }

  def removeDisabledEndpointArns(endpointArns: Seq[String])(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Response] = {
    doPost[Seq[String]]("/registrations/delete", endpointArns)
  }

  private def doGet(params: List[(String, String)])(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext) = {
    withCircuitBreaker(
      http.GET[Seq[RegistrationToken]](url("/registrations"), params)
    )
  }

  private def doPost[T](resource: String, allTheThings: T)(implicit w: Writes[T], headerCarrier: HeaderCarrier, ex: ExecutionContext) = {
    withCircuitBreaker(
      http.POST[T, HttpResponse](url(resource), allTheThings, Seq.empty).map(response => {
        response.status match {
          case status if status >= 200 && status < 300 => Success(status)
          case _ => Error(response.status)
        }
      })
    )
  }
}