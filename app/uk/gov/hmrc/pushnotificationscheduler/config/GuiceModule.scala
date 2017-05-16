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

package uk.gov.hmrc.pushnotificationscheduler.config

import com.google.inject.AbstractModule
import com.google.inject.name.Names.named
import play.api.Mode.Mode
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HttpDelete, HttpGet, HttpPost}
import uk.gov.hmrc.pushnotificationscheduler.{MicroserviceAuditConnector, WSHttp}

import scala.concurrent.duration.FiniteDuration


class GuiceModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScheduledConfig with ServicesConfig {

  override protected lazy val mode: Mode = environment.mode
  override protected lazy val runModeConfiguration: Configuration = configuration

  override def configure(): Unit = {

    bind(classOf[HttpGet]).to(classOf[WSHttp])
    bind(classOf[HttpPost]).to(classOf[WSHttp])
    bind(classOf[HttpDelete]).to(classOf[WSHttp])
    bind(classOf[AuditConnector]).to(classOf[MicroserviceAuditConnector])

    bind(classOf[Logger]).toInstance(Logger("push-notification-scheduler"))

    bind(classOf[String]).annotatedWith(named("pushRegistrationUrl")).toInstance(baseUrl("push-registration"))
    bind(classOf[String]).annotatedWith(named("pushNotificationUrl")).toInstance(baseUrl("push-notification"))
    bind(classOf[String]).annotatedWith(named("snsClientUrl")).toInstance(baseUrl("sns-client"))

    bind(classOf[Int]).annotatedWith(named("registrationTokenDispatcherCount")).toInstance(maximumSenders(configuration, "registrationTokenDispatcher"))
    bind(classOf[FiniteDuration]).annotatedWith(named("tokenExchangeInitialDelaySeconds")).toInstance(durationFromConfig(configuration, "registrationTokenExchangeJobApi", "initialDelay" ))
    bind(classOf[FiniteDuration]).annotatedWith(named("tokenExchangeIntervalSeconds")).toInstance(durationFromConfig(configuration, "registrationTokenExchangeJobApi", "interval" ))

    bind(classOf[Int]).annotatedWith(named("notificationDispatcherCount")).toInstance(maximumSenders(configuration, "notificationDispatcher"))
    bind(classOf[FiniteDuration]).annotatedWith(named("notificationSendInitialDelaySeconds")).toInstance(durationFromConfig(configuration, "notificationSendJobApi", "initialDelay" ))
    bind(classOf[FiniteDuration]).annotatedWith(named("notificationSendIntervalSeconds")).toInstance(durationFromConfig(configuration, "notificationSendJobApi", "interval" ))
  }
}
