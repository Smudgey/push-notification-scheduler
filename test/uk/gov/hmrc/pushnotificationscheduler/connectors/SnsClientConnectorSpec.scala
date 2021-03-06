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
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpReads, Upstream5xxResponse}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.domain.NativeOS.{Android, Windows, iOS}
import uk.gov.hmrc.pushnotificationscheduler.domain.{DeliveryStatus, Notification, RegistrationToken}
import uk.gov.hmrc.pushnotificationscheduler.support.WithTestApplication

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class SnsClientConnectorSpec extends UnitSpec with WithTestApplication with ServicesConfig with ScalaFutures {

  private trait Setup extends MockitoSugar {
    val mockHttp: WSHttp = mock[WSHttp]

    val connector = new SnsClientConnector("http://otherserver:8765", mockHttp)

    val unregisteredTokens = List(RegistrationToken("foo", Android), RegistrationToken("bar", iOS))
    val badTokens = List(RegistrationToken("baz", Windows), RegistrationToken("quux", iOS))
    val breakingTokens = List(RegistrationToken("garply", Android))

    val notifications = List(
      Notification("msg-1", "end:point:a", "Beautiful Railway Bridge of the Silvery Tay!", Some("1"), "windows"),
      Notification("msg-2", "end:point:a", "With your numerous arches and pillars in so grand array", Some("1"), "windows")
    )
  }

  private trait Success extends Setup {
    doReturn(successful(unregisteredTokens.map(_.token -> UUID.randomUUID().toString).toMap), Nil: _*).when(mockHttp).POST[Seq[RegistrationToken], Map[String,Option[String]]](matches(s"${connector.serviceUrl}/sns-client/endpoints"), ArgumentMatchers.eq(unregisteredTokens), any[Seq[(String, String)]])(any[Writes[Seq[RegistrationToken]]], any[HttpReads[Map[String,Option[String]]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(successful(notifications.map(_.id -> DeliveryStatus.Success).toMap), Nil: _*).when(mockHttp).POST[Seq[Notification], Map[String,DeliveryStatus]](matches(s"${connector.serviceUrl}/sns-client/notifications"), any[Seq[Notification]], any[Seq[(String, String)]])(any[Writes[Seq[Notification]]], any[HttpReads[Map[String,DeliveryStatus]]], any[HeaderCarrier], any[ExecutionContext])
  }

  private trait BadRequest extends Setup {
    doReturn(failed(new BadRequestException("BOOM!")), Nil: _*).when(mockHttp).POST[Seq[RegistrationToken], Map[String,Option[String]]](matches(s"${connector.serviceUrl}/sns-client/endpoints"), ArgumentMatchers.eq(badTokens), any[Seq[(String, String)]])(any[Writes[Seq[RegistrationToken]]], any[HttpReads[Map[String,Option[String]]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(failed(new BadRequestException("BASH!")), Nil: _*).when(mockHttp).POST[Seq[Notification], Map[String,DeliveryStatus]](matches(s"${connector.serviceUrl}/sns-client/notifications"), any[Seq[Notification]], any[Seq[(String, String)]])(any[Writes[Seq[Notification]]], any[HttpReads[Map[String,DeliveryStatus]]], any[HeaderCarrier], any[ExecutionContext])
  }

  private trait Failed extends Setup {
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).POST[Seq[RegistrationToken], Map[String,Option[String]]](matches(s"${connector.serviceUrl}/sns-client/endpoints"), ArgumentMatchers.eq(breakingTokens), any[Seq[(String, String)]])(any[Writes[Seq[RegistrationToken]]], any[HttpReads[Map[String,Option[String]]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(failed(Upstream5xxResponse("SPLAT!", 500, 500)), Nil: _*).when(mockHttp).POST[Seq[Notification], Map[String,DeliveryStatus]](matches(s"${connector.serviceUrl}/sns-client/notifications"), any[Seq[Notification]], any[Seq[(String, String)]])(any[Writes[Seq[Notification]]], any[HttpReads[Map[String,DeliveryStatus]]], any[HeaderCarrier], any[ExecutionContext])
  }

  "exchangeTokens" should {
    "succeed when a 200 response is received" in new Success {
      val result: Map[String, Option[String]] = await(connector.exchangeTokens(unregisteredTokens))

      result.keySet.toList shouldBe unregisteredTokens.map(_.token)
    }

    "throw BadRequestException when a 400 response is returned" in new BadRequest {
      intercept[BadRequestException] {
        await(connector.exchangeTokens(badTokens))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Failed {
      intercept[Upstream5xxResponse] {
        await(connector.exchangeTokens(breakingTokens))
      }
    }
  }

  "sendNotifications" should {
    "succeed when a 200 response is received" in new Success {
      val result: Map[String, DeliveryStatus] = await(connector.sendNotifications(notifications))

      result.keySet.toList shouldBe notifications.map(_.id)
      result.values.count(_ == DeliveryStatus.Success) shouldBe 2
    }

    "throw BadRequestException when a 400 response is returned" in new BadRequest {
      intercept[BadRequestException] {
        await(connector.sendNotifications(notifications))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Failed {
      intercept[Upstream5xxResponse] {
        await(connector.sendNotifications(notifications))
      }
    }
  }
}