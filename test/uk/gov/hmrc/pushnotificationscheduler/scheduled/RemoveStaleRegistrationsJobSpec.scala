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

import org.mockito.Mockito
import org.mockito.Mockito.{times, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.domain.DeletedRegistrations
import uk.gov.hmrc.pushnotificationscheduler.services.PushRegistrationService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class RemoveStaleRegistrationsJobSpec  extends UnitSpec with MockitoSugar with ScalaFutures {

  val mockPushRegistration: PushRegistrationService = mock[PushRegistrationService]
  val job = new RemoveStaleRegistrationsJob(mockPushRegistration, 1 second, 1 second)
  val expectedDeleted = DeletedRegistrations(123)

  "scheduling the RemoveStaleRegistrationsJob" should {
    "remove stale registrations" in {
      when(mockPushRegistration.removeStaleRegistrations).thenReturn(Future.successful(Some(expectedDeleted)))

      val result: job.Result = await(job.execute)

      Mockito.verify(mockPushRegistration, times(1)).removeStaleRegistrations

      result shouldBe job.Result("OK")
    }
  }
}
