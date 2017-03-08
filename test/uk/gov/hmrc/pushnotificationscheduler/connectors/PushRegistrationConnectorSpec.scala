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

import org.mockito.{ArgumentMatcher, ArgumentMatchers}
import org.mockito.ArgumentMatchers.{any, argThat, matches}
import org.mockito.Mockito.doReturn
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.http.{BadRequestException, _}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotificationscheduler.config.ServicesCircuitBreaker
import uk.gov.hmrc.pushnotificationscheduler.domain.NativeOS.{Android, Windows, iOS}
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}


class PushRegistrationConnectorSpec extends UnitSpec with WithFakeApplication with ServicesConfig with ScalaFutures with CircuitBreakerTest {

  private trait Setup extends MockitoSugar {
    implicit lazy val hc = HeaderCarrier()

    val mockHttp: WSHttp = mock[WSHttp]
    val connector = new PushRegistrationConnector with ServicesConfig with ServicesCircuitBreaker {
      override def http: WSHttp = mockHttp

      override def serviceUrl: String = "/some/end/point"
    }

    val unregisteredTokens = List(RegistrationToken("foo", Android), RegistrationToken("bar", iOS))
    val previouslyFailedTokens = List(RegistrationToken("quux", Windows))
    val invalidTokens = List(RegistrationToken("bez", iOS))

    val tokenToArns = Map("snap" -> "crackle")
    val badTokenToArnMap = Map("bad" -> "dog")
    val breakingTokenToArnMap = Map("broken" -> "heart")

    val success = 10
    val upstreamFailure = 11
    val badRequest = 12

    doReturn(successful(unregisteredTokens), Nil: _*).when(mockHttp).GET[Seq[RegistrationToken]](matches(s"${connector.serviceUrl}/registrations"), argThat(containsTuple[String, String]("maxBatchSize", success.toString)))(any[HttpReads[Seq[RegistrationToken]]](), any[HeaderCarrier]())
    doReturn(successful(previouslyFailedTokens), Nil: _*).when(mockHttp).GET[Seq[RegistrationToken]](matches(s"${connector.serviceUrl}/registrations"), argThat(containsTuple[String, String]("mode", "recover")))(any[HttpReads[Seq[RegistrationToken]]](), any[HeaderCarrier]())
    doReturn(failed(new BadRequestException("BOOM!")), Nil: _*).when(mockHttp).GET[Seq[RegistrationToken]](matches(s"${connector.serviceUrl}/registrations"), argThat(containsTuple[String, String]("maxBatchSize", badRequest.toString)))(any[HttpReads[Seq[RegistrationToken]]](), any[HeaderCarrier]())
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).GET[Seq[RegistrationToken]](matches(s"${connector.serviceUrl}/registrations"), argThat(containsTuple[String, String]("maxBatchSize", upstreamFailure.toString)))(any[HttpReads[Seq[RegistrationToken]]](), any[HeaderCarrier]())

    doReturn(successful(HttpResponse(200, None)), Nil: _*).when(mockHttp).POST[Map[String, String], HttpResponse](matches(s"${connector.serviceUrl}/registrations"), argThat(containsKey[String, String]("snap")), any[Seq[(String, String)]])(any[Writes[Map[String, String]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())
    doReturn(failed(new BadRequestException("BOOM!")), Nil: _*).when(mockHttp).POST[Map[String, String], HttpResponse](matches(s"${connector.serviceUrl}/registrations"), argThat(containsKey[String, String]("bad")), any[Seq[(String, String)]])(any[Writes[Map[String, String]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).POST[Map[String, String], HttpResponse](matches(s"${connector.serviceUrl}/registrations"), argThat(containsKey[String, String]("broken")), any[Seq[(String, String)]])(any[Writes[Map[String, String]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())

    doReturn(successful(HttpResponse(200, None)), Nil: _*).when(mockHttp).POST[Seq[RegistrationToken], HttpResponse](matches(s"${connector.serviceUrl}/registrations/delete"), ArgumentMatchers.eq(unregisteredTokens), any[Seq[(String, String)]])(any[Writes[Seq[RegistrationToken]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())
    doReturn(failed(new BadRequestException("BOOM!")), Nil: _*).when(mockHttp).POST[Seq[RegistrationToken], HttpResponse](matches(s"${connector.serviceUrl}/registrations/delete"), ArgumentMatchers.eq(previouslyFailedTokens), any[Seq[(String, String)]])(any[Writes[Seq[RegistrationToken]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).POST[Seq[RegistrationToken], HttpResponse](matches(s"${connector.serviceUrl}/registrations/delete"), ArgumentMatchers.eq(invalidTokens), any[Seq[(String, String)]])(any[Writes[Seq[RegistrationToken]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())

    doReturn(successful(HttpResponse(200, None)), Nil: _*).when(mockHttp).POST[Seq[String], HttpResponse](matches(s"${connector.serviceUrl}/registrations/delete"), ArgumentMatchers.eq(tokenToArns.values.toList), any[Seq[(String, String)]])(any[Writes[Seq[String]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())
    doReturn(failed(new BadRequestException("BOOM!")), Nil: _*).when(mockHttp).POST[Seq[String], HttpResponse](matches(s"${connector.serviceUrl}/registrations/delete"), ArgumentMatchers.eq(badTokenToArnMap.values.toList), any[Seq[(String, String)]])(any[Writes[Seq[String]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).POST[Seq[String], HttpResponse](matches(s"${connector.serviceUrl}/registrations/delete"), ArgumentMatchers.eq(breakingTokenToArnMap.values.toList), any[Seq[(String, String)]])(any[Writes[Seq[String]]](), any[HttpReads[HttpResponse]](), any[HeaderCarrier]())
  }

  "getUnregisteredTokens" should {
    "return a valid response when a 200 response is received with a valid json payload" in new Setup {
      val result: Seq[RegistrationToken] = await(connector.getUnregisteredTokens(success))
      result shouldBe unregisteredTokens
    }

    "throw BadRequestException when a 400 response is returned" in new Setup {
      intercept[BadRequestException] {
        await(connector.getUnregisteredTokens(badRequest))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      intercept[Upstream5xxResponse] {
        await(connector.getUnregisteredTokens(upstreamFailure))
      }
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      shouldTriggerCircuitBreaker(after = 5,
        connector.getUnregisteredTokens(upstreamFailure)
      )
    }
  }

  "recoverFailedRegistrations" should {
    "return a valid response when a 200 response is received with a valid json payload" in new Setup {
      val result: Seq[RegistrationToken] = await(connector.recoverFailedRegistrations(success))
      result shouldBe previouslyFailedTokens
    }

    "throw BadRequestException when a 400 response is returned" in new Setup {
      intercept[BadRequestException] {
        await(connector.getUnregisteredTokens(badRequest))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      intercept[Upstream5xxResponse] {
        await(connector.recoverFailedRegistrations(upstreamFailure))
      }
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      shouldTriggerCircuitBreaker(after = 5,
        connector.recoverFailedRegistrations(upstreamFailure)
      )
    }
  }

  "registerTokens" should {
    "succeed when a 200 response is received" in new Setup {
      val result: Response = await(connector.registerTokens(tokenToArns))
      result.status shouldBe 200
    }

    "throw BadRequestException when a 400 response is returned" in new Setup {
      intercept[BadRequestException] {
        await(connector.registerTokens(badTokenToArnMap))
      }
    }
    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      intercept[Upstream5xxResponse] {
        await(connector.registerTokens(breakingTokenToArnMap))
      }
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      shouldTriggerCircuitBreaker(after = 5,
        connector.registerTokens(breakingTokenToArnMap)
      )
    }
  }

  "removeDisabledTokens" should {
    "succeed when a 200 response is received" in new Setup {
      val result: Response = await(connector.removeDisabledTokens(unregisteredTokens))
      result.status shouldBe 200
    }

    "throw BadRequestException when a 400 response is returned" in new Setup {
      intercept[BadRequestException] {
        await(connector.removeDisabledTokens(previouslyFailedTokens))
      }
    }
    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      intercept[Upstream5xxResponse] {
        await(connector.removeDisabledTokens(invalidTokens))
      }
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      shouldTriggerCircuitBreaker(after = 5,
        connector.removeDisabledTokens(invalidTokens)
      )
    }
  }

  "removeDisabledEndpointArns" should {
    "succeed when a 200 response is received" in new Setup {
      val result: Response = await(connector.removeDisabledEndpointArns(tokenToArns.values.toList))
      result.status shouldBe 200
    }

    "throw BadRequestException when a 400 response is returned" in new Setup {
      intercept[BadRequestException] {
        await(connector.removeDisabledEndpointArns(badTokenToArnMap.values.toList))
      }
    }
    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      intercept[Upstream5xxResponse] {
        await(connector.removeDisabledEndpointArns(breakingTokenToArnMap.values.toList))
      }
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      shouldTriggerCircuitBreaker(after = 5,
        connector.removeDisabledEndpointArns(breakingTokenToArnMap.values.toList)
      )
    }
  }
}

case class containsTuple[A, B](t1: A, t2: B) extends ArgumentMatcher[Seq[(A, B)]] {
  override def matches(argument: Seq[(A, B)]): Boolean = argument.contains((t1, t2))
}

case class containsKey[A, B](key: A) extends ArgumentMatcher[Map[A,B]] {
  override def matches(argument: Map[A, B]): Boolean = argument.contains(key)
}