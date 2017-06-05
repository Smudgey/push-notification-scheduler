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
import uk.gov.hmrc.pushnotificationscheduler.actor._
import uk.gov.hmrc.pushnotificationscheduler.actor.WorkPullingPattern.{Batch, Epic, RegisterWorker}
import uk.gov.hmrc.pushnotificationscheduler.connectors.ReplyToClientConnector
import uk.gov.hmrc.pushnotificationscheduler.domain.{Callback, Notification}
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[NotificationDispatcher])
trait CallbackDispatcherApi {
  def processCallbacks(): Future[Unit]

  def isRunning: Future[Boolean]
}

@Singleton
class CallbackDispatcher @Inject()(@Named("callbackDispatcherCount") callbackDispatcherCount: Int, pushNotificationService: PushNotificationService, replyToClient:ReplyToClientConnector, system: ActorSystem, metrics: Metrics) extends CallbackDispatcherApi {
  implicit val timeout = Timeout(1, HOURS)

  lazy val gangMaster: ActorRef = system.actorOf(Props[Master[Batch[Notification]]])

  val name = "callback-dispatcher"

  (1 until callbackDispatcherCount).foreach { _ =>
    gangMaster ! RegisterWorker(system.actorOf(CallbackWorker.props(gangMaster, pushNotificationService, replyToClient, metrics)))
  }

  override def processCallbacks(): Future[Unit] = {

    for {
      queued: Batch[Callback] <- pushNotificationService.getCallbacks()
      work <- Future.successful {
        if (queued.nonEmpty)
          List(queued)
        else
          List.empty
      }
      _ <- gangMaster ? Epic[Batch[Callback]](work)
    } yield ()
  }

  override def isRunning: Future[Boolean] = Future.successful(false)
}
