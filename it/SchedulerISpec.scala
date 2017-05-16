
import org.scalatest.LoneElement
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.time.{Seconds, Span}
import play.api.libs.json._


case class Device(id:String, device:String)

case class TestNotification(id:Int, authHeaders:(String, String), internalId:String, devices:Seq[Device])

case class Template(id: String, params: Map[String, String] = Map.empty)

object Template {
  implicit val formats = Json.format[Template]
}

class SchedulerISpec extends SchedulerServiceISpec(testName = classOf[SchedulerISpec].getSimpleName, Seq(), Map.empty) with LoneElement with Eventually {

  trait NotificationTest {

    def notificationTestLifeCycle(testData:Seq[TestNotification]) = {

      // Part 1: Register devices associated with users.
      testData.foreach { test =>
        test.devices.foreach { device =>
          `/push/registration`(test.authHeaders).post(
            Json.
              parse(
                device.device)) should have(status(201))
        }
      }

      // Part 2: Wait for the scheduler to exchange the notification token for SNS device ARN.
      testData.foreach { test =>
        test.devices.foreach { device =>

          eventually(Timeout(Span(60, Seconds)), Interval(Span(2, Seconds))) {
            // Verify SNS-stub sending the request to exchange token for ARN.
            val resp = s"""{"CreatePlatformEndpoint":{"applicationArn":"default-platform-arn","registrationToken":"${device.id}"}}"""
            `/aws-sns-stub/messages/:token`(s"${device.id}").get() should have(body(resp))

            // Verify push-registration has been updated with the ARN.
            `/push/test-only/registration/$authId/$token`(test.internalId, s"${device.id}").get() should have(
              status(200),
              jsonProperty(__ \ "endpoint", s"default-platform-arn/stubbed/default-platform-arn/${device.id}")
            )
          }
        }
      }

      // Part 3: Send message to users.
      val template = Template("NGC_001", Map.empty)
      testData.foreach { test =>
        `/push-notification/message`(test.authHeaders).post(Json.toJson(template)) should have(status(201))
      }

      // Part 4: Verify messages sent to SNS are correct concerning user Id and push-notification state for message
      // has been updated to delivered.
      eventually(Timeout(Span(60, Seconds)), Interval(Span(2, Seconds))) {

        testData.foreach { test =>
          test.devices.foreach { device =>

            // Wait for the message to be delivered to SNS.
            val publishMessageResponse = s"""{"PublishRequest":{"message":"This is a push notification that does nothing else other than show you this text.","targetArn":"default-platform-arn/stubbed/default-platform-arn/${device.id}"}}"""
            `/aws-sns-stub/messages/publish-request/:token`(s"${device.id}", test.authHeaders).get() should have(body(publishMessageResponse))

            // Verify the outcome of the notification in push-notification and status set to delivered.
            `/push-notification/test-only/notification/find/:token/:internalId`(s"${device.id}", test.internalId).get() should have(
              status(200),
              jsonProperty(__ \ "endpoint", s"default-platform-arn/stubbed/default-platform-arn/${device.id}"),
              jsonProperty(__ \ "content", "This is a push notification that does nothing else other than show you this text."),
              jsonProperty(__ \ "os", "android"),
              jsonProperty(__ \ "status", "delivered"),
              jsonProperty(__ \ "attempts", 1),
              jsonProperty(__ \ "authId", test.internalId))
          }
        }
      }
    }
  }

  "notification schedulers" should {

    "successfully send notification only messages to devices where all devices have unique tokens" in new NotificationTest {

      val testData: Seq[TestNotification] = createTestNotifications(2, 3, uniqueDevices = true)

      notificationTestLifeCycle(testData)
    }

    "successfully send notification only messages to devices where all devices share the same name across users" in new NotificationTest {

      val testData: Seq[TestNotification] = createTestNotifications(2, 3, uniqueDevices = false)

      notificationTestLifeCycle(testData)
    }

  }
}