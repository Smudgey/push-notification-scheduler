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
import uk.gov.hmrc.pushnotificationscheduler.connectors.{PushRegistrationConnector, Response, Success}
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[PushRegistrationService])
trait PushRegistrationServiceApi {

  def getUnregisteredTokens: Future[Seq[RegistrationToken]]
  def recoverFailedRegistrations: Future[Seq[RegistrationToken]]
  def registerEndpoints(tokenToEndpointMap: Map[String,String]): Future[Boolean]
  def removeDisabledTokens(tokens: Seq[RegistrationToken]): Future[Boolean]
  def removeDisabledEndpoints(endpoints: Seq[String]): Future[Boolean]
}

@Singleton
class PushRegistrationService @Inject() (connector: PushRegistrationConnector) extends PushRegistrationServiceApi {
  override def getUnregisteredTokens: Future[Seq[RegistrationToken]] = {
    getTokens(connector.getUnregisteredTokens(), logFailureAs = "Failed to get unregistered tokens")
  }

  override def recoverFailedRegistrations: Future[Seq[RegistrationToken]] = {
    getTokens(connector.recoverFailedRegistrations(), logFailureAs = "Failed to recover failed tokens")
  }

  override def registerEndpoints(tokenToEndpointMap: Map[String, String]): Future[Boolean] = {
    processRequest(connector.registerEndpoints(tokenToEndpointMap), "register endpoints")
  }

  override def removeDisabledTokens(tokens: Seq[RegistrationToken]): Future[Boolean] = {
    processRequest(connector.removeDisabledTokens(tokens), "remove disabled tokens")
  }

  override def removeDisabledEndpoints(endpoints: Seq[String]): Future[Boolean] = {
    processRequest(connector.removeDisabledEndpoints(endpoints), "remove disabled endpoints")
  }

  private def getTokens(func: => Future[Seq[RegistrationToken]], logFailureAs: String = "Error") = {
    func recover {
      case e: Throwable =>
        Logger.error(s"$logFailureAs: ${e.getMessage}", e)
        Seq.empty
    }
  }

  private def processRequest(func: => Future[Response], logFailureAs: String = "process request"): Future[Boolean] = {
    func.map{ r: Response =>
      if (r.isInstanceOf[Success]) {
        true
      } else {
        Logger.error(s"Failed to $logFailureAs, status = ${r.status}")
        false
      }
    }.recover {
      case e: Throwable =>
        Logger.error(s"Failed to $logFailureAs: ${e.getMessage}")
        false
    }
  }
}