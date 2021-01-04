/*
 * Copyright 2021 HM Revenue & Customs
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

package mappings

import org.joda.time.{ LocalDate, Years }
import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.format.Formatter
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }
import play.api.data.FormError
import play.api.data.format.Formatter

import scala.language.implicitConversions
import scala.util.{ Success, Try }

case class DayMonthYear(day: String, month: String, year: String)

object DayMonthYear {
  implicit def toDate(dmy: DayMonthYear): LocalDate = new LocalDate(dmy.year.toInt, dmy.month.toInt, dmy.day.toInt)
  implicit def fromLocalDate(dmy: LocalDate): DayMonthYear = DayMonthYear(
    dmy.getDayOfMonth.toString,
    dmy.getMonthOfYear.toString,
    dmy.getYear.toString
  )

  def emptyDate: DayMonthYear = DayMonthYear("", "", "")

  def validDayMonthYear(message: String, msgForFuture: String)(minInclusive: Option[LocalDate], maxInclusive: Option[LocalDate]) =
    dayMonthYear(message) verifying validDateConstraint(message, msgForFuture)(minInclusive, maxInclusive)

  private def validDateConstraint(message: String, msgForFuture: String)(
    minInclusive: Option[LocalDate], maxInclusive: Option[LocalDate]
  ): Constraint[DayMonthYear] = {
    Constraint[DayMonthYear]("constraint.required") { dmy =>
      Try(toDate(dmy)) match {
        case Success(dt: LocalDate) if minInclusive.forall(m => m.isBefore(dt) ||
          m.isEqual(dt)) && maxInclusive.forall(m => m.isAfter(dt) || m.isEqual(dt)) =>
          Valid
        case Success(dt: LocalDate) if dt.isAfter(LocalDate.now()) => Invalid(ValidationError(msgForFuture))
        case _ => Invalid(ValidationError(message))
      }
    }
  }

  private def dayMonthYear(message: String): Mapping[DayMonthYear] = mapping(
    "day" -> text,
    "month" -> text,
    "year" -> text
  )(DayMonthYear.apply)(DayMonthYear.unapply)
}

object Year {
  type Year = String
  // scalastyle:off line.size.limit
  val yearPattern = """^([0-9]){4}$""".r
  // scalastyle:on line.size.limit

  def validYearConstraint: Constraint[Year] = Constraint[Year]("constraint.year") { year =>
    yearPattern.pattern.matcher(year).matches match {
      case true => Valid
      case false if year.isEmpty => Invalid(ValidationError("error.year.required"))
      case false => Invalid(ValidationError("error.year.invalid"))
    }
  }

  def validateYear(year: String): Boolean = yearPattern.pattern.matcher(year).matches

  val yearFormatter = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val year: Option[String] = data.get(key)

      year match {
        case None | Some("") => Left(List(FormError(key, "error.year.required")))
        case Some(m) if !m.isEmpty && !Year.validateYear(m) => Left(List(FormError(key, "error.year.format")))
        case _ => Right(year.map(_.trim))
      }
    }

    override def unbind(key: String, value: Option[String]) = Map(key -> value.map(_.trim).getOrElse(""))
  }
}
