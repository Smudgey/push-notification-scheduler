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

import javax.inject.{Named, Inject, Singleton}

import com.google.inject.ImplementedBy
import uk.gov.hmrc.play.http.{HttpDelete, HttpPost, HttpGet}
import uk.gov.hmrc.pushnotificationscheduler.domain.{ClientRequest, Callback}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[ReplyToClientConnector])
trait ReplyToClientConnectorApi extends GenericConnector {

  def replyToClient(callback:Callback): Future[Response]
}

@Singleton
class ReplyToClientConnector @Inject()(@Named("callbackUrl") val serviceUrl: String, val http: HttpGet with HttpPost with HttpDelete) extends ReplyToClientConnectorApi {

  def replyToClient(callback:Callback): Future[Response] = {
    postToResource[ClientRequest](callback.callbackUrl, ClientRequest(callback.status, callback.response))
  }
}
