/*
 * Copyright 2018 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.domain._
import uk.gov.hmrc.pushnotificationscheduler.support.WithTestApplication

import scala.concurrent.ExecutionContext
import scala.concurrent.Future._

class ReplyToClientConnectorSpec extends UnitSpec with WithTestApplication with ScalaFutures {

  private trait Setup extends MockitoSugar {
    val mockHttp: WSHttp = mock[WSHttp]

    val connector = new ReplyToClientConnector("not-used", mockHttp)

    val someUrl = "http://somehost/a"
    val callback = Callback(someUrl, PushMessageStatus.Acknowledge.toString, CallbackResponse("messageId1", Some("Yes")), 1)
  }

  private trait Success extends Setup {
    doReturn(successful(HttpResponse(200, None)), Nil: _*).when(mockHttp).POST[Map[String, String], HttpResponse](matches(someUrl), any[Map[String, String]], any[Seq[(String, String)]])(any[Writes[Map[String, String]]], any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
  }

  private trait Error extends Setup {
    doReturn(successful(HttpResponse(500, None)), Nil: _*).when(mockHttp).POST[Map[String, String], HttpResponse](matches(someUrl), any[Map[String, String]], any[Seq[(String, String)]])(any[Writes[Map[String, String]]], any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
  }

  private trait ExceptionTest extends Setup {
    doReturn(failed(new BadRequestException("Controlled explosion!")), Nil: _*).when(mockHttp).POST[Map[String, String], HttpResponse](matches(someUrl), any[Map[String, String]], any[Seq[(String, String)]])(any[Writes[Map[String, String]]], any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
  }

  "invoking the client callback connector" should {

    "succeed when a 200 response is received" in new Success {
      val result: Response = await(connector.replyToClient(callback))

      result shouldBe Success(200)
    }

    "fail when non success response is received" in new Error {
      val result: Response = await(connector.replyToClient(callback))

      result shouldBe Error(500)
    }

    "throw BadRequestException" in new ExceptionTest {

      intercept[BadRequestException] {
        await(connector.replyToClient(callback))
      }

    }
  }

}