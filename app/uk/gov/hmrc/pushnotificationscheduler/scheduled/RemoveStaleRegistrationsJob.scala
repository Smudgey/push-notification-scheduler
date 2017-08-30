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

package uk.gov.hmrc.pushnotificationscheduler.scheduled

import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import play.Logger
import uk.gov.hmrc.play.scheduling.ScheduledJob
import uk.gov.hmrc.pushnotificationscheduler.services.PushRegistrationService

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

@ImplementedBy(classOf[RemoveStaleRegistrationsJob])
trait RemoveStaleRegistrationsJobApi extends ScheduledJob {
  val name = "remove-stale-registrations"
  val initialDelay: FiniteDuration
  val interval: FiniteDuration

  val pushRegistrationService: PushRegistrationService

  override def execute(implicit hc: ExecutionContext): Future[Result] = {
    Logger.debug("Starting RemoveStaleRegistrationsJob... ")
    pushRegistrationService.removeStaleRegistrations.map{ maybeDeleted =>
      val deleted = maybeDeleted.map(_.removed.toString).getOrElse("no")
      Logger.info(s"Removed $deleted stale registrations")
      Result("OK")}
  }

  override def isRunning: Future[Boolean] = Future.successful(false)
}

@Singleton
class RemoveStaleRegistrationsJob @Inject() (val pushRegistrationService: PushRegistrationService, @Named("removeStaleRegistrationsDelaySeconds") override val initialDelay: FiniteDuration, @Named("removeStaleRegistrationsIntervalSeconds") override val interval: FiniteDuration) extends RemoveStaleRegistrationsJobApi
