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

package uk.gov.hmrc.pushnotificationscheduler.actor

import akka.actor.{ActorRef, Props}
import play.api.Logger
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services.{PushRegistrationService, SnsClientService}

import scala.concurrent.Future
import scala.util.Failure

class TokenExchangeWorker(master: ActorRef, snsClientService: SnsClientService, pushRegistrationService: PushRegistrationService, metrics: Metrics) extends Worker[Seq[RegistrationToken]](master) {
  override def doWork(work: Seq[RegistrationToken]): Future[_] = {

    snsClientService.exchangeTokens(work).map { (tokenToEndpointMap: Map[String, Option[String]]) =>
      pushRegistrationService.registerEndpoints(tokenToEndpointMap)
        .onComplete {
          case Failure(e) =>
            Logger.error(s"Failed to register endpoints: ${e.getMessage}")
            metrics.incrementTokenExchangeFailure(work.size)
            Future.failed(e)
          case _ =>
            metrics.incrementTokenExchangeSuccess(tokenToEndpointMap.values.count(_.isDefined))
            metrics.incrementTokenDisabled(tokenToEndpointMap.values.count(_.isEmpty))
            Future.successful(Unit)
        }
    }
  }
}

object TokenExchangeWorker {
  def props(master: ActorRef, snsClientService: SnsClientService, pushRegistrationService: PushRegistrationService, metrics: Metrics): Props =
    Props(new TokenExchangeWorker(master, snsClientService, pushRegistrationService, metrics))
}
