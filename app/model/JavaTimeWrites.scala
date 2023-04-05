/*
 * Copyright 2023 HM Revenue & Customs
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

package model

import java.time._
//import org.joda.time._
import play.api.libs.json.{JsNumber, JsString, JsValue, Writes}

object JavaTimeWrites extends JavaTimeWrites

trait JavaTimeWrites {

  /**
    * Serializer DateTime -> JsNumber(d.getMillis (number of milliseconds since the Epoch))
    */
  object JavaTimeInstantNumberWrites extends Writes[Instant] {
    // this "Writes" is storing java time Instant as NumberLong in Mongo using only upto Millis for compatibility,
    // nanos are not included, nanos are an addition in java time, that was not present in Joda time.
    def writes(d: Instant): JsValue = JsNumber(d.toEpochMilli)
  }

  /**
    * Default Serializer LocalDate -> JsString(ISO8601 format (yyyy-MM-dd))
    */
  implicit object JavaTimeInstantWrites extends Writes[Instant] {
    def writes(d: Instant): JsValue = JsString(d.toString)
  }

//  /**
//    * Serializer for LocalDate
//    * @param pattern the pattern used by org.joda.time.format.DateTimeFormat
//    */
//  def jodaLocalDateWrites(pattern: String): Writes[LocalDate] = {
//    val df = org.joda.time.format.DateTimeFormat.forPattern(pattern)
//    Writes[LocalDate] { d =>
//      JsString(d.toString(df))
//    }
//  }
//
//  /**
//    * Default Serializer LocalDate -> JsString(ISO8601 format (yyyy-MM-dd))
//    */
//  implicit object DefaultJodaLocalDateWrites extends Writes[LocalDate] {
//    def writes(d: LocalDate): JsValue = JsString(d.toString)
//  }
//
//  /**
//    * Serializer for LocalTime
//    * @param pattern the pattern used by org.joda.time.format.DateTimeFormat
//    */
//  def jodaLocalTimeWrites(pattern: String): Writes[LocalTime] =
//    Writes[LocalTime] { d =>
//      JsString(d.toString(pattern))
//    }
//
//  /**
//    * Default Serializer LocalDate -> JsString(ISO8601 format (HH:mm:ss.SSS))
//    */
//  implicit object DefaultJodaLocalTimeWrites extends Writes[LocalTime] {
//    def writes(d: LocalTime): JsValue = JsString(d.toString)
//  }
}
