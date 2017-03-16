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

package uk.gov.hmrc.pushnotificationscheduler.metrics

import javax.inject.Singleton
import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import uk.gov.hmrc.play.graphite.MicroserviceMetrics

@ImplementedBy(classOf[Metrics])
trait MetricsApi {
  def registry: com.codahale.metrics.MetricRegistry

  def incrementTokenExchangeSuccess(n: Long)
  def incrementTokenExchangeFailure(n: Long)
  def incrementTokenDisabled(n: Long)
}

@Singleton
class Metrics extends MetricsApi with MicroserviceMetrics {
  lazy val registry: MetricRegistry = metrics.defaultRegistry

  val scheduler = "push-notification-scheduler"
  val successful = s"$scheduler.successful"
  val failed = s"$scheduler.failed"

  override def incrementTokenExchangeSuccess(n: Long): Unit = registry.meter(s"$successful.token-exchange").mark(n)
  override def incrementTokenExchangeFailure(n: Long): Unit = registry.meter(s"$failed.token-exchange").mark(n)
  override def incrementTokenDisabled(n: Long): Unit = registry.meter(s"$successful.token-disabled").mark(n)
}
