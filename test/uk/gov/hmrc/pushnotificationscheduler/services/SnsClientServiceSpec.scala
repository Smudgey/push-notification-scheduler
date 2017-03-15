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
import org.mockito.ArgumentMatchers.{any, anyInt}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.http.{HttpReads, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.connectors.SnsClientConnectorApi
import uk.gov.hmrc.pushnotificationscheduler.domain.NativeOS.{Android, Windows}
import uk.gov.hmrc.pushnotificationscheduler.domain.RegistrationToken

import scala.concurrent.{ExecutionContext, Future}

class SnsClientServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {
  private trait Setup extends MockitoSugar {
    val connector = mock[SnsClientConnectorApi]

    val service = new SnsClientService(connector)

    val expectedTokens = List(RegistrationToken("foo", Android), RegistrationToken("bar", Windows))
    val expectedMap = Map(expectedTokens map {t => (t.token, Option("/endpoint/" + t.token))} : _*)
  }

  "SnsClientService.exchangeTokens" should {
    "return a map of tokens to endpoints" in new Setup {
      when(connector.exchangeTokens(any[Seq[RegistrationToken]])(any[HttpReads[Map[String,String]]](), any[ExecutionContext]())).thenReturn(Future.successful(expectedMap))

      val result = await(service.exchangeTokens(expectedTokens))

      val captor: ArgumentCaptor[Seq[RegistrationToken]] = ArgumentCaptor.forClass(classOf[Seq[RegistrationToken]])
      verify(connector).exchangeTokens(captor.capture())(any[HttpReads[Map[String,String]]](), any[ExecutionContext]())

      captor.getValue shouldBe expectedTokens
      result shouldBe expectedMap
    }

    "return an empty map when there is a problem with the SNS client service" in new Setup {
      when(connector.exchangeTokens(any[Seq[RegistrationToken]])(any[HttpReads[Map[String,String]]](), any[ExecutionContext]())).thenReturn(Future.failed(Upstream5xxResponse("Kaboom!", 500, 500)))

      val result = await(service.exchangeTokens(expectedTokens))

      result.size shouldBe 0
    }
  }
}