
import org.scalatest.LoneElement
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.time.{Seconds, Span}
import play.api.libs.json._


class SchedulerMessageISpec extends SchedulerServiceISpec(testName = classOf[SchedulerMessageISpec].getSimpleName, Seq(), Map.empty) with LoneElement with Eventually {

  trait MessageIdSupport {
    val messageIdMap = scala.collection.mutable.Map[Int, String]()

    def getMessageIdFromKey(id:Int) = messageIdMap.get(id).fold(throw new IllegalArgumentException("Failed to resolve Id"))
      {found => found}
  }

  trait NotificationMessageTest extends MessageIdSupport {

    def notificationTestLifeCycle(testData:Seq[TestNotification]) = {

      // Part 1: Register devices associated to users.
      testData.foreach { test =>
        test.devices.foreach { device =>
          `/push/registration`(test.authHeaders).post(
            Json.parse(device.device)) should have(status(201))
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
      testData.foreach { test =>
        val template = Template("NGC_003",
          Map("title" -> "mr", "firstName" -> "bob", "lastName" -> "bright", "agent" -> "some-agent",
            "callbackUrl" -> "http://localhost:8245/aws-sns-stub/callback",
            // TODO: The messageId key can be dropped.
            "messageId" -> "TODO-remove-this-key"))

        val resp = `/push-notification/message`(test.authHeaders).post(Json.toJson(template))
        resp  should have(status(201))
        
        val messageId: String = (resp.json \ "messageId").as[String]
        messageIdMap += (test.id -> messageId)
      }

      // Part 4: Verify messages sent to SNS are correct concerning user Id and push-notification state for message
      // has been updated to delivered.
      eventually(Timeout(Span(60, Seconds)), Interval(Span(10, Seconds))) {

        testData.foreach { test =>
          test.devices.foreach { device =>

            val messageId = getMessageIdFromKey(test.id)

            // Wait for the message to be delivered to SNS.
            val publishMessageResponse = s"""{"PublishRequest":{"message":"{\\"GCM\\":\\"{\\\\\\"notification\\\\\\":{\\\\\\"body\\\\\\":\\\\\\"mr bob bright, HMRC has sent you a message\\\\\\"},\\\\\\"data\\\\\\":{\\\\\\"messageId\\\\\\":\\\\\\"$messageId\\\\\\"}}\\"}","targetArn":"default-platform-arn/stubbed/default-platform-arn/${device.id}"}}"""
            `/aws-sns-stub/messages/publish-request/:token`(s"${device.id}", test.authHeaders).get() should have(body(publishMessageResponse))

            // Verify the outcome of the notification in push-notification and message was sent to the correct user.
            `/push-notification/test-only/notification/find/:token/:internalId`(s"${device.id}", test.internalId).get() should have(
              status(200),
              jsonProperty(__ \ "endpoint", s"default-platform-arn/stubbed/default-platform-arn/${device.id}"),
              jsonProperty(__ \ "messageId", messageId),
              jsonProperty(__ \ "content", "mr bob bright, HMRC has sent you a message"),
              jsonProperty(__ \ "os", "android"),
              jsonProperty(__ \ "status", "delivered"),
              jsonProperty(__ \ "attempts", 1),
              jsonProperty(__ \ "authId", test.internalId))
          }
        }
      }

      // Part 5: Update the status of the message from acknowledge to acknowledged.
      testData.foreach { test =>
        val messageId = getMessageIdFromKey(test.id)

        `/push-notification/messages/:id/status`(test.authHeaders, messageId).post(Json.toJson(Response(messageId))) should have(status(200))
      }

      // Part 6: Check the Stub was invoked to acknowledge receipt of the message.
      // TODO: Replace below call with new mobile app endpoint to retrieve the message based on the Id. Service will auto
      // TODO: update the state of the message.
      eventually(Timeout(Span(120, Seconds)), Interval(Span(30, Seconds))) {
        testData.foreach { test =>
          val messageId = getMessageIdFromKey(test.id)
          `/aws-sns-stub/callback/:messageId`(test.authHeaders, s"$messageId-acknowledge").get() should have(status(200))
        }
      }

      // Part 7: Answer the question.
      testData.foreach { test =>
        val messageId = getMessageIdFromKey(test.id)

        `/push-notification/messages/:id/status`(test.authHeaders, messageId).post(Json.toJson(Response(messageId, Some("Yes")))) should have(status(200))
      }

      // Part 8: Check the Stub was invoked to callback the client with the answer response.
      eventually(Timeout(Span(120, Seconds)), Interval(Span(30, Seconds))) {

        testData.foreach { test =>
          val messageId = getMessageIdFromKey(test.id)
          `/aws-sns-stub/callback/:messageId`(test.authHeaders, s"$messageId-answer").get() should have(status(200))
        }
      }
    }
  }

  "notification message scheduler" should {

    "successfully process life cycle of message notifications where the device Id" in new NotificationMessageTest {
      val testData: Seq[TestNotification] = createTestNotifications(5, 3, uniqueDevices = true)

      notificationTestLifeCycle(testData)
    }

  }
}