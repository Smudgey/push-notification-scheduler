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

package uk.gov.hmrc.pushnotificationscheduler.domain

import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

case class NativeOSTest(id: Int, nativeOS: NativeOS)

class JsonSerialisationSpec extends UnitSpec {
  val someToken = "some-token-123"

  Seq(NativeOSTest(OS.iOS, NativeOS.iOS), NativeOSTest(OS.Android, NativeOS.Android), NativeOSTest(OS.Windows, NativeOS.Windows)).foreach { item =>

    "Json serialization/deserialize" should {

      s"successfully serialize RegistrationToken for OS range $item" in {
        val token = RegistrationToken(someToken, item.nativeOS)

        Json.toJson(token) shouldBe Json.parse(s"""{"token":"$someToken","os":"${item.nativeOS}"}""")
      }

      s"successfully deserialize Device for OS range $item" in {
        val tokenJson = s"""{"token":"$someToken","os":"${item.nativeOS}"}"""

        val actualToken = Json.parse(tokenJson).as[RegistrationToken]
        actualToken shouldBe RegistrationToken(someToken, item.nativeOS)
      }
    }
  }

  "JSON deserialize " should {

    "fail to create RegistrationToken when OS is not recognised" in {
      a[Exception] should be thrownBy Json.parse( s"""{"token":"token","os":"quux"}""").as[RegistrationToken]
    }
  }

}
