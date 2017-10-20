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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.Logger
import uk.gov.hmrc.http.{HttpException, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.connectors.{Error, PushRegistrationConnector, Success}
import uk.gov.hmrc.pushnotificationscheduler.domain.NativeOS.{Android, Windows}
import uk.gov.hmrc.pushnotificationscheduler.domain.{DeletedRegistrations, RegistrationToken}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

class PushRegistrationServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  private trait Setup extends MockitoSugar {
    val connector = mock[PushRegistrationConnector]
    val logger = mock[Logger]

    val service = new PushRegistrationService(connector, logger)

    val expectedTokens = List(RegistrationToken("foo", Android), RegistrationToken("bar", Windows))
    val expectedMap = Map("token" -> Option("endpoint"), "otherToken" -> None)
    val expectedEndpoints = List("fee", "fi", "fo", "fum")
    val expectedDeleted = DeletedRegistrations(5)
  }

  "PushRegistrationService.getUnregisteredTokens" should {

    "return unregistered tokens when unregistered tokens are available" in new Setup {
      when(connector.getUnregisteredTokens()(any[ExecutionContext]())).thenReturn(Future.successful(expectedTokens))

      val result = await(service.getUnregisteredTokens)

      result shouldBe expectedTokens
    }

    "return an empty list when no unregistered tokens are available" in new Setup {
      when(connector.getUnregisteredTokens()(any[ExecutionContext])).thenReturn(Future.failed(new HttpException("Move along!", 404)))

      val result = await(service.getUnregisteredTokens)

      result.size shouldBe 0
    }

    "return an empty list when the push registration service is not available" in new Setup {
      when(connector.getUnregisteredTokens()(any[ExecutionContext])).thenReturn(Future.failed(new HttpException("Failed to acquire lock", 503)))

      val result = await(service.getUnregisteredTokens)

      result.size shouldBe 0
    }

    "return an empty list when the push registration service fails" in new Setup {
      when(connector.getUnregisteredTokens()(any[ExecutionContext])).thenReturn(Future.failed(Upstream5xxResponse("Kaboom!", 500, 500)))

      val result = await(service.getUnregisteredTokens)

      result.size shouldBe 0
    }
  }

  "PushRegistrationService.recoverFailedRegistrations" should {

    "return previously failed tokens when such tokens are available" in new Setup {
      when(connector.recoverFailedRegistrations()(any[ExecutionContext]())).thenReturn(Future.successful(expectedTokens))

      val result = await(service.recoverFailedRegistrations)

      result shouldBe expectedTokens
    }

    "return an empty list when no unregistered tokens are available" in new Setup {
      when(connector.recoverFailedRegistrations()(any[ExecutionContext])).thenReturn(Future.failed(new HttpException("Move along!", 404)))

      val result = await(service.recoverFailedRegistrations)

      result.size shouldBe 0
    }

    "return an empty list when the push registration service fails" in new Setup {
      when(connector.recoverFailedRegistrations()(any[ExecutionContext])).thenReturn(Future.failed(Upstream5xxResponse("Kaboom!", 500, 500)))

      val result = await(service.recoverFailedRegistrations)

      result.size shouldBe 0
    }
  }

  "PushRegistrationService.registerEndpoints" should {
    "return success when it has successfully registered endpoints" in new Setup {
      when(connector.registerEndpoints(any[Map[String,Option[String]]])(any[ExecutionContext])).thenReturn(Future.successful(Success(200)))

      val result = await(service.registerEndpoints(expectedMap))

      val captor: ArgumentCaptor[Map[String,Option[String]]] = ArgumentCaptor.forClass(classOf[Map[String,Option[String]]])
      verify(connector).registerEndpoints(captor.capture())(any[ExecutionContext])

      captor.getValue shouldBe expectedMap

      result.onComplete{
        case Failure(_) => fail("should have succeeded")
        case _ => // all good
      }
    }

    "return failure when it can when it cannot save endpoint details because of a problem with the remote service " in new Setup {
      when(connector.registerEndpoints(any[Map[String,Option[String]]])(any[ExecutionContext])).thenReturn(Future.successful(Error(400)))

      val result = await(service.registerEndpoints(expectedMap))

      result.onComplete{
        case Failure(e) => e.getMessage should contain ("400")
        case _ => fail("should not have succeeded")
      }
    }

    "return failure when it cannot save endpoint details because of a remote service failure" in new Setup {
      when(connector.registerEndpoints(any[Map[String,Option[String]]])(any[ExecutionContext])).thenReturn(Future.failed(Upstream5xxResponse("Kaboom!", 500, 500)))

      val result = await(service.registerEndpoints(expectedMap))

      result.onComplete{
        case Failure(e) => e.getMessage should contain ("500")
        case _ => fail("should not have succeeded")
      }
    }
  }

  "PushRegistrationService.removeStaleRegistrations" should {
    "return success when it has successfully deleted stale registrations" in new Setup {
      when(connector.removeStaleRegistrations()(any[ExecutionContext]())).thenReturn(Future.successful(expectedDeleted))

      val result: Option[DeletedRegistrations] = await(service.removeStaleRegistrations)

      result shouldBe Some(expectedDeleted)
    }

    "return none when the push registration service fails" in new Setup {
      when(connector.removeStaleRegistrations()(any[ExecutionContext])).thenReturn(Future.failed(Upstream5xxResponse("Kaboom!", 500, 500)))

      val result = await(service.removeStaleRegistrations)

      result shouldBe None
    }
  }
}
