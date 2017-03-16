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
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HttpDelete, HttpGet, HttpPost}
import uk.gov.hmrc.pushnotificationscheduler.config.ServicesCircuitBreaker
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PushRegistrationConnector])
trait PushRegistrationConnectorApi extends GenericConnector with ServicesConfig with ServicesCircuitBreaker {
  this: ServicesCircuitBreaker =>

  override val externalServiceName: String = "push-registration"

  def getUnregisteredTokens(maxBatchSize: Int = defaultBatchSize)(implicit ex: ExecutionContext): Future[Seq[RegistrationToken]] = {
    get[Seq[RegistrationToken]]("/push/registration", List(("maxBatchSize", maxBatchSize.toString)))
  }

  def recoverFailedRegistrations(maxBatchSize: Int = defaultBatchSize)(implicit ex: ExecutionContext): Future[Seq[RegistrationToken]] = {
    get[Seq[RegistrationToken]]("/push/registration", List(("mode", "recover"), ("maxBatchSize", maxBatchSize.toString)))
  }

  def registerEndpoints(tokenToEndpointMap: Map[String,Option[String]])(implicit ex: ExecutionContext): Future[Response] = {
    post[Map[String, Option[String]]]("/push/registration", tokenToEndpointMap)
  }

  def removeDisabledEndpoints(endpoints: Seq[String])(implicit ex: ExecutionContext): Future[Response] = {
    post[Seq[String]]("/push/registration/delete", endpoints)
  }
}

@Singleton
class PushRegistrationConnector @Inject()(@Named("pushRegistrationUrl") val serviceUrl: String, val http: HttpGet with HttpPost with HttpDelete) extends PushRegistrationConnectorApi with ServicesConfig with ServicesCircuitBreaker
