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
import uk.gov.hmrc.pushnotificationscheduler.dispatchers.NotificationDispatcher

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NotificationSendJob])
trait NotificationSendJobApi extends ScheduledJob {

  val name = "notification-send"
  val initialDelay: FiniteDuration
  val interval: FiniteDuration

  val notificationDispatcher: NotificationDispatcher

  override def execute(implicit hc: ExecutionContext): Future[Result] = {
    Logger.debug("Starting NotificationSendJob... ")
    notificationDispatcher.processNotifications().map(_ => Result("OK"))
  }

  override def isRunning: Future[Boolean] = notificationDispatcher.isRunning
}

@Singleton
class NotificationSendJob @Inject() (val notificationDispatcher: NotificationDispatcher, @Named("notificationSendInitialDelaySeconds") override val initialDelay: FiniteDuration, @Named("notificationSendIntervalSeconds") override val interval: FiniteDuration) extends NotificationSendJobApi
