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

import play.api.Logger
import uk.gov.hmrc.play.http.HttpException
import uk.gov.hmrc.pushnotificationscheduler.connectors.{Response, Success}

import scala.concurrent.{ExecutionContext, Future}

trait EntityManager {
  val entities: String = "data"

  def fetch[T](func: => Future[Seq[T]])(implicit ec: ExecutionContext): Future[Seq[T]] = {
    func recover {
      case e: HttpException if e.responseCode == 404 =>
        Logger.info(s"No $entities found")
        Seq.empty
      case e: Throwable =>
        Logger.error(s"Failed to fetch $entities: ${e.getMessage}", e)
        Seq.empty
    }
  }

  def update(func: => Future[Response])(implicit ec: ExecutionContext): Future[_] = {
    func.map{ r: Response => r match {
      case _: Success =>
        Future.successful(Unit)
      case _ =>
        Logger.error(s"Failed to update $entities, status = ${r.status}")
        Future.failed(new HttpException(s"Failed to update $entities", r.status))
    }}.recover {
      case e: Throwable =>
        Logger.error(s"Failed to update $entities: ${e.getMessage}")
        Future.failed(e)
    }
  }
}