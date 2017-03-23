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

import scala.concurrent.ExecutionContext

sealed trait Response {
  def status: Int
}

case class Success(status: Int) extends Response

case class Error(status: Int) extends Response

trait GenericConnector {

  implicit lazy val hc = HeaderCarrier()

  val defaultBatchSize = 10

  val externalServiceName = "some-service"

  val http: HttpGet with HttpPost with HttpDelete

  val serviceUrl: String

  def url(path: String) = s"$serviceUrl$path"

  def get[T](resource: String, params: List[(String, String)])(implicit r: HttpReads[T], ex: ExecutionContext) =
    http.GET[T](url(resource), params)


  def submit[T, U](resource: String, data: T)(implicit r: HttpReads[U], w: Writes[T], ex: ExecutionContext) =
    http.POST[T, U](url(resource), data, Seq.empty)

  def post[T](resource: String, data: T)(implicit w: Writes[T], ex: ExecutionContext) = {
    submit[T, HttpResponse](resource, data).map(response => {
      response.status match {
        case status if status >= 200 && status < 300 => Success(status)
        case _ => Error(response.status)
      }
    })
  }
}
