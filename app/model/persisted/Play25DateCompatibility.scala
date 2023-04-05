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

package model.persisted

import model.{JavaTimeReads, JavaTimeWrites}
import org.joda.time.DateTime
import play.api.libs.json._

import java.time.Instant

// Provides backward compatibility for Play 2.5 play-json, which stores dates as epoch milliseconds
// The new play-json-joda lib that we use with Play 2.6 writes dates using ISO8601 by default eg.
// "expiryDate": "2019-07-31T23:59:59.395+01:00"
//
// Old play-json writes in epoch milliseconds eg.
// "expiryDate": 1564613999395”
object Play25DateCompatibility {

    implicit val epochMillisDateFormat = new Format[DateTime] {
      override def reads(json: JsValue): JsResult[DateTime] = JodaReads.DefaultJodaDateTimeReads.reads(json)
      override def writes(o: DateTime): JsValue = JodaWrites.JodaDateTimeNumberWrites.writes(o)
    }

  implicit val javaTimeInstantEpochMillisDateFormat = new Format[Instant] {
    override def reads(json: JsValue): JsResult[Instant] = JavaTimeReads.DefaultJavaTimeDateTimeReads.reads(json)
    override def writes(o: Instant): JsValue = JavaTimeWrites.JavaTimeInstantNumberWrites.writes(o)
  }
}
