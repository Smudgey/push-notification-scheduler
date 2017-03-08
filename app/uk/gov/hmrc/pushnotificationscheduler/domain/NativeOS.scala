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

import play.api.libs.json._

import scala.math.BigDecimal

trait NativeOS

object NativeOS {
  val ios = "ios"
  val android = "android"
  val windows = "windows"

  case object iOS extends NativeOS {
    override def toString: String = ios
  }
  case object Android extends NativeOS {
    override def toString: String = android
  }

  case object Windows extends NativeOS {
    override def toString: String = windows
  }

  val reads: Reads[NativeOS] = new Reads[NativeOS] {
    override def reads(json: JsValue): JsResult[NativeOS] = json match {
      case JsString("ios") => JsSuccess(iOS)
      case JsString("android") => JsSuccess(Android)
      case JsString("windows") => JsSuccess(Windows)
      case _ => throw new Exception(s"Failed to resolve $json")
    }
  }

  val readsFromStore: Reads[NativeOS] = new Reads[NativeOS] {
    override def reads(json: JsValue): JsResult[NativeOS] =
      json match {
        case JsNumber(value: BigDecimal) if value == OS.getId(iOS) => JsSuccess(iOS)
        case JsNumber(value: BigDecimal) if value == OS.getId(Android) => JsSuccess(Android)
        case JsNumber(value: BigDecimal) if value == OS.getId(Windows) => JsSuccess(Windows)
      }
  }

  val writes: Writes[NativeOS] = new Writes[NativeOS] {
    override def writes(os: NativeOS): JsString = os match {
      case `iOS` => JsString(ios)
      case Android => JsString(android)
      case Windows => JsString(windows)
    }
  }

  implicit val formats = Format(NativeOS.reads, NativeOS.writes)
}

object OS {
  final val iOS = 1
  final val Android = 2
  final val Windows = 3
  val validOS = Seq((NativeOS.iOS, iOS), (NativeOS.Android, Android), (NativeOS.Windows, Windows))

  def getId(nativeOS:NativeOS): Int = {
    validOS.find( p => p._1 == nativeOS).fold(throw new Exception(s"Failed to resolve the input OS value $nativeOS"))(res => res._2)
  }
}
