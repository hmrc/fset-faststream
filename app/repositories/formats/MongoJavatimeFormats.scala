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

/**
  * Based on uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats trait that is in
  * uk.gov.hmrc.mongo:hmrc-mongo-play-28
  * This allows us to store OffsetDateTime in mongo in the same way as when we used JodaTime eg.
  *
  * ISODate("2023-12-06T18:36:53.244+0000")
  *
  * and not the following when using java time:
  *
  * 2023-12-06T18:36:53.244809Z
 */
package repositories.formats

import play.api.libs.json._

import java.time.{Instant, OffsetDateTime, ZoneId}

trait MongoJavatimeFormats {
  outer =>

  // LocalDate
/*
  final val localDateReads: Reads[LocalDate] =
    Reads.at[String](__ \ "$date" \ "$numberLong")
      .map(date =>
        new LocalDate(date.toLong, DateTimeZone.UTC)
      )

  final val localDateWrites: Writes[LocalDate] =
    Writes.at[String](__ \ "$date" \ "$numberLong")
      .contramap[LocalDate](_.toDateTimeAtStartOfDay(DateTimeZone.UTC).getMillis.toString)

  final val localDateFormat: Format[LocalDate] =
    Format(localDateReads, localDateWrites)
*/
  // LocalDateTime
/*
  final val localDateTimeReads: Reads[LocalDateTime] =
    Reads.at[String](__ \ "$date" \ "$numberLong")
      .map(dateTime => new LocalDateTime(dateTime.toLong, DateTimeZone.UTC))

  final val localDateTimeWrites: Writes[LocalDateTime] =
    Writes.at[String](__ \ "$date" \ "$numberLong")
      .contramap[LocalDateTime](_.toDateTime(DateTimeZone.UTC).getMillis.toString)

  final val localDateTimeFormat: Format[LocalDateTime] =
    Format(localDateTimeReads, localDateTimeWrites)
*/
  // DateTime

  final val offsetDateTimeReads: Reads[OffsetDateTime] =
    Reads.at[String](__ \ "$date" \ "$numberLong")
      .map(dateTime => OffsetDateTime.ofInstant(Instant.ofEpochMilli(dateTime.toLong), ZoneId.of("UTC")))

  final val offsetDateTimeWrites: Writes[OffsetDateTime] =
    Writes.at[String](__ \ "$date" \ "$numberLong")
      .contramap[OffsetDateTime](_.toInstant.toEpochMilli.toString)

  final val offsetDateTimeFormat: Format[OffsetDateTime] =
    Format(offsetDateTimeReads, offsetDateTimeWrites)

  trait Implicits {
//    implicit val jotLocalDateFormat    : Format[LocalDate]     = outer.localDateFormat
//    implicit val jotLocalDateTimeFormat: Format[LocalDateTime] = outer.localDateTimeFormat
//    implicit val jotDateTimeFormat     : Format[DateTime]      = outer.dateTimeFormat
    implicit val jtOffsetDateTimeFormat     : Format[OffsetDateTime]      = outer.offsetDateTimeFormat
  }

  object Implicits extends Implicits
}

object MongoJavatimeFormats extends MongoJavatimeFormats
