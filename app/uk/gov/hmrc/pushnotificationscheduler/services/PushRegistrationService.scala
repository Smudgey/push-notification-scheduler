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
import uk.gov.hmrc.pushnotificationscheduler.connectors.PushRegistrationConnectorApi
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[PushRegistrationService])
trait PushRegistrationServiceApi extends EntityManager {
  override val entities: String = "push registrations"

  def getUnregisteredTokens: Future[Seq[RegistrationToken]]
  def recoverFailedRegistrations: Future[Seq[RegistrationToken]]
  def registerEndpoints(tokenToEndpointMap: Map[String,Option[String]]): Future[_]
}

@Singleton
class PushRegistrationService @Inject() (connector: PushRegistrationConnectorApi) extends PushRegistrationServiceApi {
  override def getUnregisteredTokens: Future[Seq[RegistrationToken]] = {
    fetch[RegistrationToken](connector.getUnregisteredTokens())
  }

  override def recoverFailedRegistrations: Future[Seq[RegistrationToken]] = {
    fetch[RegistrationToken](connector.recoverFailedRegistrations())
  }

  override def registerEndpoints(tokenToEndpointMap: Map[String, Option[String]]): Future[_] = {
    update(connector.registerEndpoints(tokenToEndpointMap))
  }
}