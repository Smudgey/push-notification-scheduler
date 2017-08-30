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
import uk.gov.hmrc.pushnotificationscheduler.domain.{DeletedRegistrations, RegistrationToken}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PushRegistrationConnector])
trait PushRegistrationConnectorApi extends GenericConnector with ServicesConfig {

  override val externalServiceName: String = "push-registration"

  def getUnregisteredTokens()(implicit ex: ExecutionContext): Future[Seq[RegistrationToken]] = {
    get[Seq[RegistrationToken]]("/push/endpoint/incomplete", List.empty[(String,String)])
  }

  def recoverFailedRegistrations()(implicit ex: ExecutionContext): Future[Seq[RegistrationToken]] = {
    get[Seq[RegistrationToken]]("/push/endpoint/timedout", List.empty[(String,String)])
  }

// TODO...the update must contain the InternalAuthId associated with the token.
  def registerEndpoints(tokenToEndpointMap: Map[String,Option[String]])(implicit ex: ExecutionContext): Future[Response] = {
    post[Map[String, Option[String]]]("/push/endpoint", tokenToEndpointMap)
  }

  def removeStaleRegistrations()(implicit ex: ExecutionContext): Future[DeletedRegistrations] = {
    delete[DeletedRegistrations]("/push/endpoint/stale")
  }
}

@Singleton
class PushRegistrationConnector @Inject()(@Named("pushRegistrationUrl") val serviceUrl: String, val http: HttpGet with HttpPost with HttpDelete) extends PushRegistrationConnectorApi with ServicesConfig
