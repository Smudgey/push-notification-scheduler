/*
 * Copyright 2018 HM Revenue & Customs
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
import play.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.pushnotificationscheduler.actor.WorkPullingPattern.Batch
import uk.gov.hmrc.pushnotificationscheduler.connectors.ReplyToClientConnector
import uk.gov.hmrc.pushnotificationscheduler.domain.{Callback, CallbackResult}
import uk.gov.hmrc.pushnotificationscheduler.metrics.Metrics
import uk.gov.hmrc.pushnotificationscheduler.services._

import scala.concurrent.Future

case class CallbackResultBatch(batch: Seq[CallbackResult])

object CallbackResultBatch {
  implicit val formats = Json.format[CallbackResultBatch]
}

case class CallbackBatch(batch: Seq[Callback])

object CallbackBatch {
  implicit val formats = Json.format[CallbackBatch]
}

class CallbackWorker(master: ActorRef, pushNotificationService: PushNotificationService, client:ReplyToClientConnector, metrics: Metrics) extends Worker[Batch[Callback]](master) {

  def incrementNotificationFailure():Unit = metrics.incrementNotificationCallbackUpdateFailure(1)

  def debug(message:String) = Logger.debug(s"CallbackWorker: $message")

  def error(message:String) = Logger.error(s"CallbackWorker: $message")
  
  override def doWork(work: Batch[Callback]): Future[Unit] = {

    val callbackOutcome: Seq[Future[CallbackResult]] = work.map { item =>
      debug("Processing message " + item)
      client.replyToClient(item).map { res =>
        CallbackResult(item.response.messageId, item.status, if (res.status == 200) true else false)
      }.recover {
        case ex: Exception =>
          error(s"Failed to invoke client with URL ${item.callbackUrl}. Exception is ${ex.getMessage}")
          CallbackResult(item.response.messageId, item.status, success = false)
      }
    }

    Future.sequence(callbackOutcome).flatMap{ outcome =>
      metrics.incrementCallbackSuccess(outcome.count(_.success == true))
      metrics.incrementCallbackFailure(outcome.count(_.success == false))

      pushNotificationService.updateCallbacks(CallbackResultBatch(outcome)).map { result =>
        if (!result) {
          error(s"Failed to update push-notification. Status returned indicated the service did not update!")
          incrementNotificationFailure()
        }
        result
      }.recover {
        case ex: Exception =>
          error(s"Failed to update push-notification without outcome. Exception is ${ex.getMessage}")
          incrementNotificationFailure()
          false
      }
    }.map{ updated =>
      debug(s"Status of the push notification update result is $updated" )
    }
  }
}

object CallbackWorker {

  def props(master: ActorRef, pushNotificationService: PushNotificationService, replyToClient:ReplyToClientConnector, metrics: Metrics): Props = {
    Props(new CallbackWorker(master, pushNotificationService, replyToClient, metrics))
  }
}
