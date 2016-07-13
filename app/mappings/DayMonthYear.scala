/*
 * Copyright 2016 HM Revenue & Customs
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

import org.joda.time.LocalDate
import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }

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
