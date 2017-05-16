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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, verifyZeroInteractions, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.Logger
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.connectors.{Error, GenericConnector, Response, Success}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

class EntityManagerSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  private trait ThingsConnector extends GenericConnector {
    def getThings()(implicit ex: ExecutionContext): Future[Seq[String]]
    def updateThings(things: Seq[String])(implicit ex: ExecutionContext): Future[Response]
  }

  private trait Setup extends MockitoSugar {
    val mockLogger = mock[org.slf4j.Logger]
    val mockConnector = mock[ThingsConnector]
    val stringCaptor = ArgumentCaptor.forClass(classOf[String])

    val service = new EntityManager {
      override val logger: Logger = new Logger(mockLogger)
    }

    when(mockLogger.isInfoEnabled).thenReturn(true)
    when(mockLogger.isWarnEnabled()).thenReturn(true)
    when(mockLogger.isErrorEnabled()).thenReturn(true)

    val someData = Seq("foo", "bar", "baz")
  }

  private trait Success extends Setup {
    when(mockConnector.getThings()(any[ExecutionContext]())).thenReturn(successful(someData))
    when(mockConnector.updateThings(any[Seq[String]])(any[ExecutionContext]())).thenReturn(successful(Success(200)))
  }

  private trait NotFound extends Setup {
    when(mockConnector.getThings()(any[ExecutionContext]())).thenReturn(failed(new HttpException("nothing found", 404)))
    when(mockConnector.updateThings(any[Seq[String]])(any[ExecutionContext]())).thenReturn(successful(Error(404)))
  }

  private trait Unavailable extends Setup {
    when(mockConnector.getThings()(any[ExecutionContext]())).thenReturn(failed(new HttpException("unable to acquire lock", 503)))
  }

  private trait Failed extends Setup {
    when(mockConnector.getThings()(any[ExecutionContext]())).thenReturn(failed(Upstream5xxResponse("service failed", 500, 500)))
    when(mockConnector.updateThings(any[Seq[String]])(any[ExecutionContext]())).thenReturn(failed(Upstream5xxResponse("service failed", 500, 500)))
  }

  "fetch" should {
    "not log a message given a 200 response from the downstream service" in new Success {

      await(service.fetch[String](mockConnector.getThings()))

      verifyZeroInteractions(mockLogger)
    }

    "log an info message given a 404 response from the downstream service" in new NotFound {
      await(service.fetch[String](mockConnector.getThings()))

      verify(mockLogger).info(stringCaptor.capture())

      val message: String = stringCaptor.getValue

      message shouldBe "No data found"
    }

    "log a warning given a 503 response from the downstream service" in new Unavailable {
      await(service.fetch[String](mockConnector.getThings()))

      verify(mockLogger).warn(stringCaptor.capture())

      val message: String = stringCaptor.getValue

      message shouldBe "data service temporarily not available"
    }

    "log an error given a 500 response from the downstream service" in new Failed {
      await(service.fetch[String](mockConnector.getThings()))

      verify(mockLogger).error(stringCaptor.capture(), ArgumentMatchers.any[Throwable]())

      val message: String = stringCaptor.getValue

      message shouldBe "Failed to fetch data: service failed"
    }
  }

  "update" should {
    "not log a message when an update was successful" in new Success {
      await(service.update(mockConnector.updateThings(someData)))

      verifyZeroInteractions(mockLogger)
    }

    "log an error when an update is not successful" in new NotFound {
      await(service.update(mockConnector.updateThings(someData)))

      verify(mockLogger).error(stringCaptor.capture())

      val message: String = stringCaptor.getValue

      message shouldBe "Failed to update data, status = 404"
    }

    "log an error given a downstream service failure" in new Failed {
      await(service.update(mockConnector.updateThings(someData)))

      verify(mockLogger).error(stringCaptor.capture(), ArgumentMatchers.any[Throwable]())

      val message: String = stringCaptor.getValue

      message shouldBe "Failed to update data: service failed"
    }
  }
}
