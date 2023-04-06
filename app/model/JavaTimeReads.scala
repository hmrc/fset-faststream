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

/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

import org.joda.time.DateTime

import java.time._
import org.joda.time.format._
import play.api.libs.json.{JsError, JsNumber, JsPath, JsResult, JsString, JsSuccess, JsValue, JsonValidationError, Reads}

object JavaTimeReads extends JavaTimeReads

trait JavaTimeReads {

  /**
    * Reads for the `org.joda.time.DateTime` type.
    *
    * @param pattern a date pattern, as specified in `org.joda.time.format.DateTimeFormat`, or "" to use ISO format.
    * @param corrector a simple string transformation function that can be used to transform input String before parsing.
    *                  Useful when standards are not respected and require a few tweaks. Defaults to identity function.
    */
  def javaTimeInstantReads(pattern: String, corrector: String => String = identity): Reads[Instant] = new Reads[Instant] {
    // TODO MIGUEL: Not sure if it is the same
    //    val df = if (pattern == "") ISODateTimeFormat.dateOptionalTimeParser else DateTimeFormat.forPattern(pattern)
    //val df = if (pattern == "") java.time.format.DateTimeFormatter.ISO_DATE_TIME else java.time.format.DateTimeFormatter.ofPattern(pattern)

    def reads(json: JsValue): JsResult[Instant] = {
      //scalastyle:off
      println(s"-----MIGUEL reads json:[$json]")
      json match {
        case JsNumber(d) =>
          println(s"-----MIGUEL JSNumber d:[$d]")
          JsSuccess(Instant.ofEpochMilli(d.toLong))
        case JsString(s) =>
          println(s"-----MIGUEL JsString s:[$s]")
          parseDate(corrector(s)) match {
            case Some(d) =>
              println(s"-----MIGUEL Some(d) d:[$d]")
              JsSuccess(d)
            case _       => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.javatimedate.format", pattern))))
          }
        case _ =>
          println(s"-----MIGUEL JSError")
          JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.date"))))
      }
    }

    private def parseDate(input: String): Option[Instant] = {
      //scala.util.control.Exception.nonFatalCatch[Instant].opt(Instant.parse(input, df))
      // TODO MIGUEL: It users ISO_INSTANT
      scala.util.control.Exception.nonFatalCatch[Instant].opt(Instant.parse(input))
    }
  }

  /**
    * The default implicit JodaDate reads, using yyyy-MM-dd format
    */
  val JavaTimeInstantReads = javaTimeInstantReads("yyyy-MM-dd")

  /**
    * The default implicit JodaDate reads, using ISO-8601 format
    */
  implicit val DefaultJavaTimeInstantReads = javaTimeInstantReads("")


  def javaTimeOffsetDateTimeReads(pattern: String, corrector: String => String = identity): Reads[OffsetDateTime] = new Reads[OffsetDateTime] {
    // TODO MIGUEL: Not sure if it is the same
    //    val df = if (pattern == "") ISODateTimeFormat.dateOptionalTimeParser else DateTimeFormat.forPattern(pattern)
    //val df = if (pattern == "") java.time.format.DateTimeFormatter.ISO_DATE_TIME else java.time.format.DateTimeFormatter.ofPattern(pattern)

    def reads(json: JsValue): JsResult[OffsetDateTime] = json match {
      case JsNumber(d) => JsSuccess(Instant.ofEpochMilli(d.toLong).atOffset(ZoneOffset.UTC))
      case JsString(s) =>
        parseDate(corrector(s)) match {
          case Some(d) => JsSuccess(d)
          case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.javatimedate.format", pattern))))
        }
      case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.date"))))
    }

    private def parseDate(input: String): Option[OffsetDateTime] = {
      //scala.util.control.Exception.nonFatalCatch[Instant].opt(Instant.parse(input, df))
      // TODO MIGUEL: It users ISO_INSTANT
      scala.util.control.Exception.nonFatalCatch[OffsetDateTime].opt(Instant.parse(input).atOffset(ZoneOffset.UTC))
    }
  }

  /**
    * The default implicit JodaDate reads, using yyyy-MM-dd format
    */
  val JavaTimeOffsetDateTimeReads = javaTimeOffsetDateTimeReads("yyyy-MM-dd")

  /**
    * The default implicit JodaDate reads, using ISO-8601 format
    */
  implicit val DefaultJavaTimeOffsetDateTimeReads = javaTimeOffsetDateTimeReads("")



  //  /**
//    * Reads for the `org.joda.time.LocalDate` type.
//    *
//    * @param pattern a date pattern, as specified in `org.joda.time.format.DateTimeFormat`, or "" to use ISO format.
//    * @param corrector string transformation function (See jodaDateReads). Defaults to identity function.
//    */
//  def jodaLocalDateReads(pattern: String, corrector: String => String = identity): Reads[org.joda.time.LocalDate] =
//    new Reads[org.joda.time.LocalDate] {
//      val df = if (pattern == "") ISODateTimeFormat.localDateParser else DateTimeFormat.forPattern(pattern)
//
//      def reads(json: JsValue): JsResult[LocalDate] = json match {
//        case JsString(s) =>
//          parseDate(corrector(s)) match {
//            case Some(d) => JsSuccess(d)
//            case _       => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.jodadate.format", pattern))))
//          }
//        case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.date"))))
//      }
//
//      private def parseDate(input: String): Option[LocalDate] =
//        scala.util.control.Exception.nonFatalCatch[LocalDate].opt(LocalDate.parse(input, df))
//    }
//
//  /**
//    * The default implicit joda.time.LocalDate reads, using ISO-8601 format.
//    */
//  implicit val DefaultJodaLocalDateReads = jodaLocalDateReads("")
//
//  /**
//    * Reads for the `org.joda.time.LocalTime` type.
//    *
//    * @param pattern a date pattern, as specified in `org.joda.time.format.DateTimeFormat`, or "" to use ISO format.
//    * @param corrector string transformation function (See jodaTimeReads). Defaults to identity function.
//    */
//  def jodaLocalTimeReads(pattern: String, corrector: String => String = identity): Reads[LocalTime] =
//    new Reads[LocalTime] {
//      val df = if (pattern == "") ISODateTimeFormat.localTimeParser else DateTimeFormat.forPattern(pattern)
//
//      def reads(json: JsValue): JsResult[LocalTime] = json match {
//        case JsNumber(n) => JsSuccess(new LocalTime(n.toLong))
//        case JsString(s) =>
//          parseTime(corrector(s)) match {
//            case Some(d) => JsSuccess(d)
//            case None    => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.jodatime.format", pattern))))
//          }
//        case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.time"))))
//      }
//
//      private def parseTime(input: String): Option[LocalTime] =
//        scala.util.control.Exception.nonFatalCatch[LocalTime].opt(LocalTime.parse(input, df))
//    }
//
//  /**
//    * the default implicit joda.time.LocalTime reads
//    */
//  implicit val DefaultJodaLocalTimeReads = jodaLocalTimeReads("")
}
