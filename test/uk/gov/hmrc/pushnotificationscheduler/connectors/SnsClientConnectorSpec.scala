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

import java.util.UUID

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, matches}
import org.mockito.Mockito.doReturn
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.pushnotificationscheduler.domain.NativeOS.{Android, Windows, iOS}
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class SnsClientConnectorSpec extends UnitSpec with WithFakeApplication with ServicesConfig with ScalaFutures with CircuitBreakerTest {

  private trait Setup extends MockitoSugar {
    val mockHttp: WSHttp = mock[WSHttp]

    val connector = new SnsClientConnector("http://otherserver:8765", mockHttp)

    val unregisteredTokens = List(RegistrationToken("foo", Android), RegistrationToken("bar", iOS))
    val badTokens = List(RegistrationToken("baz", Windows), RegistrationToken("quux", iOS))
    val breakingTokens = List(RegistrationToken("garply", Android))

    doReturn(successful(unregisteredTokens.map(_.token -> UUID.randomUUID().toString).toMap), Nil: _*).when(mockHttp).POST[Seq[RegistrationToken], Map[String,Option[String]]](matches(s"${connector.serviceUrl}/sns-client/endpoints"), ArgumentMatchers.eq(unregisteredTokens), any[Seq[(String, String)]])(any[Writes[Seq[RegistrationToken]]](), any[HttpReads[Map[String,Option[String]]]](), any[HeaderCarrier]())
    doReturn(failed(new BadRequestException("BOOM!")), Nil: _*).when(mockHttp).POST[Seq[RegistrationToken], Map[String,Option[String]]](matches(s"${connector.serviceUrl}/sns-client/endpoints"), ArgumentMatchers.eq(badTokens), any[Seq[(String, String)]])(any[Writes[Seq[RegistrationToken]]](), any[HttpReads[Map[String,Option[String]]]](), any[HeaderCarrier]())
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).POST[Seq[RegistrationToken], Map[String,Option[String]]](matches(s"${connector.serviceUrl}/sns-client/endpoints"), ArgumentMatchers.eq(breakingTokens), any[Seq[(String, String)]])(any[Writes[Seq[RegistrationToken]]](), any[HttpReads[Map[String,Option[String]]]](), any[HeaderCarrier]())

  }

  "exchangeTokens" should {
    "succeed when a 200 response is received" in new Setup {
      val result: Map[String, Option[String]] = await(connector.exchangeTokens(unregisteredTokens))

      result.keySet.toList shouldBe unregisteredTokens.map(_.token)
    }

    "throw BadRequestException when a 400 response is returned" in new Setup {
      intercept[BadRequestException] {
        await(connector.exchangeTokens(badTokens))
      }
    }
    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      intercept[Upstream5xxResponse] {
        await(connector.exchangeTokens(breakingTokens))
      }
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      shouldTriggerCircuitBreaker(after = 5,
        connector.exchangeTokens(breakingTokens)
      )
    }
  }
}