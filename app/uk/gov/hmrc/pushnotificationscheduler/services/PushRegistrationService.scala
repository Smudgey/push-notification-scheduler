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
import uk.gov.hmrc.play.http.HttpException
import uk.gov.hmrc.pushnotificationscheduler.connectors.{PushRegistrationConnectorApi, Response, Success}
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[PushRegistrationService])
trait PushRegistrationServiceApi {

  def getUnregisteredTokens: Future[Seq[RegistrationToken]]
  def recoverFailedRegistrations: Future[Seq[RegistrationToken]]
  def registerEndpoints(tokenToEndpointMap: Map[String,Option[String]]): Future[_]
}

@Singleton
class PushRegistrationService @Inject() (connector: PushRegistrationConnectorApi) extends PushRegistrationServiceApi {
  override def getUnregisteredTokens: Future[Seq[RegistrationToken]] = {
    getTokens(connector.getUnregisteredTokens(), logAs = "unregistered")
  }

  override def recoverFailedRegistrations: Future[Seq[RegistrationToken]] = {
    getTokens(connector.recoverFailedRegistrations(), logAs = "previously failed")
  }

  override def registerEndpoints(tokenToEndpointMap: Map[String, Option[String]]): Future[_] = {
    processRequest(connector.registerEndpoints(tokenToEndpointMap), "register endpoints")
  }

  private def getTokens(func: => Future[Seq[RegistrationToken]], logAs: String) = {
    func recover {
      case e: HttpException if e.responseCode == 404 =>
        Logger.info(s"no $logAs tokens found")
        Seq.empty
      case e: Throwable =>
        Logger.error(s"Failed to get $logAs tokens: ${e.getMessage}", e)
        Seq.empty
    }
  }

  private def processRequest(func: => Future[Response], logFailureAs: String): Future[_] = {
    func.map{ r: Response =>
      if (r.isInstanceOf[Success]) {
        Future.successful(Unit)
      } else {
        Logger.error(s"Failed to $logFailureAs, status = ${r.status}")
        Future.failed(new HttpException(s"Failed to $logFailureAs", r.status))
      }
    }.recover {
      case e: Throwable =>
        Logger.error(s"Failed to $logFailureAs: ${e.getMessage}")
        Future.failed(e)
    }
  }
}