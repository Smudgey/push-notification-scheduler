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

import org.mockito.ArgumentMatchers.{any, argThat, matches}
import org.mockito.Mockito.doReturn
import org.mockito.{ArgumentMatcher, ArgumentMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.domain.NativeOS.{Android, Windows, iOS}
import uk.gov.hmrc.pushnotificationscheduler.domain.{DeletedRegistrations, RegistrationToken}
import uk.gov.hmrc.pushnotificationscheduler.support.WithTestApplication

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}


class PushRegistrationConnectorSpec extends UnitSpec with WithTestApplication with ScalaFutures {

  private trait Setup extends MockitoSugar {
    val mockHttp: WSHttp = mock[WSHttp]

    val connector = new PushRegistrationConnector("http://somehost:1234", mockHttp)

    val unregisteredTokens = List(RegistrationToken("foo", Android), RegistrationToken("bar", iOS))
    val previouslyFailedTokens = List(RegistrationToken("quux", Windows))
    val invalidTokens = List(RegistrationToken("bez", iOS))
    val deletedRegistrations = DeletedRegistrations(5)

    val tokenToArns = Map("snap" -> Option("crackle"))
    val badTokenToArnMap = Map("bad" -> Option("dog"))
    val breakingTokenToArnMap = Map("broken" -> Option("heart"))
  }

  private trait Success extends Setup {
    doReturn(successful(unregisteredTokens), Nil: _*).when(mockHttp).GET[Seq[RegistrationToken]](matches(s"${connector.serviceUrl}/push/endpoint/incomplete"), any[Seq[(String,String)]]())(any[HttpReads[Seq[RegistrationToken]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(successful(previouslyFailedTokens), Nil: _*).when(mockHttp).GET[Seq[RegistrationToken]](matches(s"${connector.serviceUrl}/push/endpoint/timedout"), any[Seq[(String,String)]]())(any[HttpReads[Seq[RegistrationToken]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(successful(HttpResponse(200, None)), Nil: _*).when(mockHttp).POST[Map[String, String], HttpResponse](matches(s"${connector.serviceUrl}/push/endpoint"), argThat(containsKey[String, String]("snap")), any[Seq[(String, String)]])(any[Writes[Map[String, String]]](), any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(successful(deletedRegistrations), Nil: _*).when(mockHttp).DELETE[DeletedRegistrations](matches(s"${connector.serviceUrl}/push/endpoint/stale"))(any[HttpReads[DeletedRegistrations]], any[HeaderCarrier], any[ExecutionContext])
  }

  private trait BadRequest extends Setup {
    doReturn(failed(new BadRequestException("BOOM!")), Nil: _*).when(mockHttp).GET[Seq[RegistrationToken]](ArgumentMatchers.startsWith(s"${connector.serviceUrl}/push/endpoint/"), any[Seq[(String,String)]]())(any[HttpReads[Seq[RegistrationToken]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(failed(new BadRequestException("BOOM!")), Nil: _*).when(mockHttp).POST[Map[String, String], HttpResponse](matches(s"${connector.serviceUrl}/push/endpoint"), argThat(containsKey[String, String]("bad")), any[Seq[(String, String)]])(any[Writes[Map[String, String]]](), any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(failed(new BadRequestException("BOOM!")), Nil: _*).when(mockHttp).DELETE[DeletedRegistrations](matches(s"${connector.serviceUrl}/push/endpoint/stale"))(any[HttpReads[DeletedRegistrations]], any[HeaderCarrier], any[ExecutionContext])
  }

  private trait UpstreamFailure extends Setup {
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).GET[Seq[RegistrationToken]](ArgumentMatchers.startsWith(s"${connector.serviceUrl}/push/endpoint/"), any[Seq[(String,String)]]())(any[HttpReads[Seq[RegistrationToken]]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).POST[Map[String, String], HttpResponse](matches(s"${connector.serviceUrl}/push/endpoint"), argThat(containsKey[String, String]("broken")), any[Seq[(String, String)]])(any[Writes[Map[String, String]]](), any[HttpReads[HttpResponse]], any[HeaderCarrier], any[ExecutionContext])
    doReturn(failed(Upstream5xxResponse("KAPOW!", 500, 500)), Nil: _*).when(mockHttp).DELETE[DeletedRegistrations](matches(s"${connector.serviceUrl}/push/endpoint/stale"))(any[HttpReads[DeletedRegistrations]], any[HeaderCarrier], any[ExecutionContext])
  }

  "getUnregisteredTokens" should {
    "return a valid response when a 200 response is received with a valid json payload" in new Success {
      val result: Seq[RegistrationToken] = await(connector.getUnregisteredTokens())
      result shouldBe unregisteredTokens
    }

    "throw BadRequestException when a 400 response is returned" in new BadRequest {
      intercept[BadRequestException] {
        await(connector.getUnregisteredTokens())
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new UpstreamFailure {
      intercept[Upstream5xxResponse] {
        await(connector.getUnregisteredTokens())
      }
    }
  }

  "recoverFailedRegistrations" should {
    "return a valid response when a 200 response is received with a valid json payload" in new Success {
      val result: Seq[RegistrationToken] = await(connector.recoverFailedRegistrations())
      result shouldBe previouslyFailedTokens
    }

    "throw BadRequestException when a 400 response is returned" in new BadRequest {
      intercept[BadRequestException] {
        await(connector.getUnregisteredTokens())
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new UpstreamFailure {
      intercept[Upstream5xxResponse] {
        await(connector.recoverFailedRegistrations())
      }
    }
  }

  "registerEndpoints" should {
    "succeed when a 200 response is received" in new Success {
      val result: Response = await(connector.registerEndpoints(tokenToArns))
      result.status shouldBe 200
    }

    "throw BadRequestException when a 400 response is returned" in new BadRequest {
      intercept[BadRequestException] {
        await(connector.registerEndpoints(badTokenToArnMap))
      }
    }
    "throw Upstream5xxResponse when a 500 response is returned" in new UpstreamFailure {
      intercept[Upstream5xxResponse] {
        await(connector.registerEndpoints(breakingTokenToArnMap))
      }
    }
  }

  "removeStaleRegistrations" should {
    "return a valid response when a 200 response is received with a valid json payload" in new Success {
      val result: DeletedRegistrations = await(connector.removeStaleRegistrations())
      result shouldBe deletedRegistrations
    }

    "throw BadRequestException when a 400 response is returned" in new BadRequest {
      intercept[BadRequestException] {
        await(connector.removeStaleRegistrations())
      }
    }
    "throw Upstream5xxResponse when a 500 response is returned" in new UpstreamFailure {
      intercept[Upstream5xxResponse] {
        await(connector.removeStaleRegistrations())
      }
    }
  }
}

case class containsKey[A, B](key: A) extends ArgumentMatcher[Map[A,B]] {
  override def matches(argument: Map[A, B]): Boolean = argument.contains(key)
}