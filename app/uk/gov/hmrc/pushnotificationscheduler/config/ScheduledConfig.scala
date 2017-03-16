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

import play.api.Configuration

import scala.concurrent.duration.{FiniteDuration,_}

trait ScheduledConfig {

  def durationFromConfig(configuration: Configuration, configKey: String, propertyKey: String): FiniteDuration = {
    configuration.getMilliseconds(s"microservice.scheduling.$configKey.$propertyKey")
      .getOrElse(throw new IllegalStateException(s"Config key scheduling.$configKey.$propertyKey missing")).millisecond
  }

  def maximumSenders(configuration: Configuration, configKey: String): Int = {
    configuration.getInt(s"microservice.throttling.$configKey.maximumSenders")
      .getOrElse(throw new IllegalStateException(s"Config key throttling.$configKey.maximumSenders missing"))
  }
}
