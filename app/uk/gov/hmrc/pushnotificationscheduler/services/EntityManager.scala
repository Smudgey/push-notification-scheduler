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
  val logger: Logger

  def fetch[T](func: => Future[Seq[T]])(implicit ec: ExecutionContext): Future[Seq[T]] = {
    func recover {
      case e: HttpException if e.responseCode == 404 =>
        logger.info(s"No $entities found")
        Seq.empty[T]
      case e: HttpException if e.responseCode == 503 =>
        logger.warn(s"$entities service temporarily not available")
        Seq.empty[T]
      case e: Throwable =>
        logger.error(s"Failed to fetch $entities: ${e.getMessage}", e)
        Seq.empty[T]
    }
  }

  def update(func: => Future[Response])(implicit ec: ExecutionContext): Future[Unit] = {
    func.flatMap { r: Response =>
      r match {
        case _: Success =>
          Future.successful(())
        case _ =>
          logger.error(s"Failed to update $entities, status = ${r.status}")
          Future.failed(new HttpException(s"Failed to update $entities", r.status))
      }
    }.recoverWith {
      case e: Throwable =>
        logger.error(s"Failed to update $entities: ${e.getMessage}", e)
        Future.failed(e)
    }
  }

  def delete[T](func: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] = {
    func.map(Some(_)) recover {
      case e =>
        logger.error(s"Failed to delete $entities: ${e.getMessage}", e)
        None
    }
  }
}
