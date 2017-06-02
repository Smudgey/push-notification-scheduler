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

package uk.gov.hmrc.pushnotificationscheduler.domain

import play.api.libs.json.Json

case class CallbackResponse(messageId: String, answer: Option[String] = None)

object CallbackResponse {
  implicit val formats = Json.format[CallbackResponse]
}

case class Callback(callbackUrl: String, status: String, response: CallbackResponse, attempt: Int)

case class ClientRequest(status: String, response: CallbackResponse)

object ClientRequest {
  implicit val format = Json.format[ClientRequest]
}

object Callback {
  implicit val formats = Json.format[Callback]
}

case class CallbackResult(messageId: String, status: String, success: Boolean)

object CallbackResult {
  implicit val formats = Json.format[CallbackResult]
}

trait PushMessageStatus

object PushMessageStatus {
  val statuses = List("acknowledge", "acknowledged", "answer", "answered", "timeout", "timed-out", "failed")

  case object Acknowledge extends PushMessageStatus {
    override def toString: String = statuses.head
  }

  case object Acknowledged extends PushMessageStatus {
    override def toString: String = statuses(1)
  }

  case object Answer extends PushMessageStatus {
    override def toString: String = statuses(2)
  }

  case object Answered extends PushMessageStatus {
    override def toString: String = statuses(3)
  }

  case object Timeout extends PushMessageStatus {
    override def toString: String = statuses(4)
  }

  case object Timedout extends PushMessageStatus {
    override def toString: String = statuses(5)
  }

  case object PermanentlyFailed extends PushMessageStatus {
    override def toString: String = statuses(6)
  }
}
