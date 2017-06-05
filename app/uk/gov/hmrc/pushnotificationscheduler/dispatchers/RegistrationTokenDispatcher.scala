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

package uk.gov.hmrc.pushnotificationscheduler.dispatchers

import java.util.concurrent.TimeUnit.HOURS
import javax.inject.{Inject, Named, Singleton}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.ImplementedBy
import uk.gov.hmrc.pushnotificationscheduler.actor.WorkPullingPattern.{Batch, Epic, RegisterWorker}
import uk.gov.hmrc.pushnotificationscheduler.actor.{Master, TokenExchangeWorker}
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services.{PushRegistrationService, SnsClientService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[RegistrationTokenDispatcher])
trait RegistrationTokenDispatcherApi {
  def exchangeRegistrationTokensForEndpoints(): Future[Unit]

  def isRunning: Future[Boolean]
}

@Singleton
class RegistrationTokenDispatcher @Inject()(@Named("registrationTokenDispatcherCount") tokenDispatcherCount: Int, snsClientService: SnsClientService, pushRegistrationService: PushRegistrationService, system: ActorSystem, metrics: Metrics) extends RegistrationTokenDispatcherApi {
  implicit val timeout = Timeout(1, HOURS)

  lazy val gangMaster: ActorRef = system.actorOf(Props[Master[Batch[RegistrationToken]]])

  val name = "registration-token-dispatcher"

  (1 until tokenDispatcherCount).foreach { _ =>
    gangMaster ! RegisterWorker(system.actorOf(TokenExchangeWorker.props(gangMaster, snsClientService, pushRegistrationService, metrics)))
  }

  // TODO: decide how often failed registrations should be retried
  override def exchangeRegistrationTokensForEndpoints(): Future[Unit] = {
    for {
          unregisteredTokens: Batch[RegistrationToken] <- pushRegistrationService.getUnregisteredTokens
          recoveredTokens: Batch[RegistrationToken] <- pushRegistrationService.recoverFailedRegistrations
          work <- Future.successful {
            if (unregisteredTokens.nonEmpty || recoveredTokens.nonEmpty)
              List(unregisteredTokens ++ recoveredTokens)
            else
              List.empty
          }
          _ <- gangMaster ? Epic[Batch[RegistrationToken]](work)
        } yield ()
  }

  override def isRunning: Future[Boolean] = Future.successful(false)
}