
import java.util.UUID

import play.api.libs.ws._
import play.mvc.Http.HeaderNames
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.play.it.{ExternalService, MongoMicroServiceEmbeddedServer, ServiceSpec}


case class Response(messageId: String, answer: Option[String] = None)

object Response {
  implicit val formats = Json.format[Response]
}

class SchedulerIntegrationServer(override val testName: String, override val externalServices: Seq[ExternalService], override val additionalConfig: Map[String, String]) extends MongoMicroServiceEmbeddedServer

object SchedulerServiceISpec

abstract class SchedulerServiceISpec(testName: String, services: Seq[ExternalService], additionalConfig: Map[String, String])
  extends ServiceSpec with ScalaFutures with ResponseMatchers with BeforeAndAfterAll with BeforeAndAfterEach {
  self =>

  override val server = new SchedulerIntegrationServer(testName, services, additionalConfig)

  lazy val httpClient = play.api.Play.current.injector.instanceOf[WSClient]

  override def beforeEach(): Unit = {
    resetMongoRepositories
  }

    def createTestNotifications(numberOfUsers: Int, numberOfDevices: Int, uniqueDevices: Boolean): Seq[TestNotification] = {
      val generator = new Generator(1)

      (1 to numberOfUsers).map { id =>
        val authority = `/auth/create-authority`(generator.nextNino)
        val authHeaders: (String, String) = authority._1
        val internalId: String = authority._2

        def device(deviceId: String) = s"""{
                                         |  "token": "$deviceId",
                                         |  "device": {
                                         |    "os": "android",
                                         |    "osVersion": "7.0",
                                         |    "appVersion": "1.0",
                                         |    "model": "Nexus 5"
                                         |  }
                                         |}""".stripMargin

        val devices = for (i <- 1 to numberOfDevices) yield {
          val devId = if (uniqueDevices) s"some-token-${UUID.randomUUID().toString}" else s"some-token-$i"
          Device(devId, device(devId))
        }
        TestNotification(id, authHeaders, internalId, devices)
      }
    }

    def resetMongoRepositories() = {
      `/push/test-only/drop-all-records`.get() should have(status(200)) // todo...switch to delete
      `/aws-sns-stub/drop-all-records`().delete() should have(status(200))
//      `/push-notification/test-only/notification/dropmongo`.delete() should have(status(200))
      `/aws-sns-stub/callback/reset`.delete() should have(status(200))
    }

    def authResource(path: String): String = {
      s"http://localhost:8500$path"
    }

    def `/auth/test-only/auth/remove-all` = {
      httpClient.url(authResource("/test-only/auth/remove-all"))
    }

    def `/push/test-only/drop-all-records`: WSRequest = {
      httpClient.url("http://localhost:8235/test-only/dropmongo")
    }

    def `/push/test-only/registration/$authId/$token`(authId:String, token:String): WSRequest = {
      httpClient.url(s"http://localhost:8235/test-only/registration/$authId/$token")
    }

    def `/push/registration`(headers: (String, String)): WSRequest = {
      httpClient.url("http://localhost:8235/push/registration")
        .withHeaders(headers, ("Accept", "application/vnd.hmrc.1.0+json"))
    }

    def `/push-notification/test-only/notification/dropmongo`: WSRequest = {
      httpClient.url("http://localhost:8246/test-only/notification/dropmongo")
    }

    // TODO...NO SERVER CONTEXT!!!  push-notification
    def `/push-notification/message`(headers: (String, String)): WSRequest = {
      httpClient.url(s"http://localhost:8246/messages").withHeaders(headers, ("Accept", "application/vnd.hmrc.1.0+json"))
    }

    def `/push-notification/test-only/notification/find/:token/:internalId`(token:String, internalId:String): WSRequest = {
      httpClient.url(s"http://localhost:8246/test-only/notification/find/$token/$internalId")
    }

    def `/push-notification/messages/:id/status`(headers: (String, String),id:String) = {
      httpClient.url(s"http://localhost:8246/messages/$id/status").withHeaders(headers, ("Accept", "application/vnd.hmrc.1.0+json"))
    }

    def `/push-notification/callbacks/undelivered`(headers: (String, String)) =
      httpClient.url(s"http://localhost:8246/callbacks/undelivered").withHeaders(headers, ("Accept", "application/vnd.hmrc.1.0+json"))

    def `/aws-sns-stub/drop-all-records`(): WSRequest = {
      httpClient.url("http://localhost:8245/aws-sns-stub/messages")
    }

    def `/aws-sns-stub/callback/reset`() : WSRequest = {
      httpClient.url(s"http://localhost:8245/aws-sns-stub/callback/reset")
    }

    def `/aws-sns-stub/messages/:token`(token:String) : WSRequest = {
      httpClient.url(s"http://localhost:8245/aws-sns-stub/messages/$token")
    }

    def `/aws-sns-stub/messages/publish-request/:token`(token:String, headers: (String, String)) : WSRequest = {
      httpClient.url(s"http://localhost:8245/aws-sns-stub/messages/publish-request/$token")//.withHeaders(headers, ("Accept", "application/vnd.hmrc.1.0+json"))
    }

    def `/aws-sns-stub/callback/:messageId`(headers: (String, String), token:String) : WSRequest = {
      httpClient.url(s"http://localhost:8245/aws-sns-stub/callback/$token")//.withHeaders(headers, ("Accept", "application/vnd.hmrc.1.0+json"))
    }

    def `/auth/create-authority`(nino:Nino): ((String, String), String) = {
      
      val enrolments = s"""{
                          |"individualEnrolments":{
                          |"nino":"${nino.value}"
                          |}
                          |}""".stripMargin

      httpClient.url(
        authResource(s"/auth/gg/${nino.value}/sign-in")).withHeaders("Content-Type" -> "application/json")
        .post(Json.parse(enrolments)) should have(status(200))

      val exchange = httpClient.url(
        authResource(s"/auth/gg/${nino.value}/exchange")).withHeaders("Content-Type" -> "application/json")
        .post("{}").futureValue
      val bearerToken = (exchange.json \\ "authToken").head.as[String]
      val authHeader = (HeaderNames.AUTHORIZATION, bearerToken)

      // TODO: Remove once push-notification removes CL=200 check.
      def updateConfidenceLevel(confidenceLevel: Int): Future[Unit] = {
        httpClient.url(authResource("/auth/authority"))
          .withHeaders(authHeader).withHeaders("Content-Type" -> "application/json")
          .patch(Json.obj(("confidenceLevel", confidenceLevel)))
          .map(res => ())
      }
      updateConfidenceLevel(200).futureValue

      val authority: WSResponse = httpClient.url(
        authResource("/auth/authority"))
        .withHeaders(authHeader)
        .get().futureValue
      val ids = (authority.json \ "ids").as[String]

      val internalId = httpClient.url(authResource(ids))
        .withHeaders(authHeader)
        .get().futureValue
      val authInternalId = (internalId.json \ "internalId").as[String]

      (authHeader, authInternalId)
    }

}