/*
 * Copyright 2022 HM Revenue & Customs
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

import model.ApplicationStatus.ApplicationStatus
import model.{ ApplicationStatus, ProgressStatuses, SchemeId, UniqueIdentifier }
import model.EvaluationResults.Result
import model.ProgressStatuses.ProgressStatus
import model.persisted.eventschedules.{ EventType, SkillType, VenueType }
import org.joda.time.LocalDate
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import play.api.mvc.{ PathBindable, QueryStringBindable }

import scala.util.{ Failure, Right, Success, Try }

package object controllers {

  object Binders {

    implicit def pathBindableUniqueIdentifier = new PathBindable[UniqueIdentifier] {
      def bind(key: String, value: String): Either[String, UniqueIdentifier] =
        Try { UniqueIdentifier(value) } match {
          case Success(v) => Right(v)
          case Failure(_: IllegalArgumentException) => Left(s"Badly formatted UniqueIdentifier $value")
          case Failure(e) => throw e
        }
      def unbind(key: String, value: UniqueIdentifier) = value.toString()
    }

    implicit def queryBindableUniqueIdentifier(implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[UniqueIdentifier] {
      def bind(key: String, params: Map[String, Seq[String]]) =
        for {
          uuid <- stringBinder.bind(key, params)
        } yield {
          uuid match {
            case Right(value) => Try { UniqueIdentifier(value) } match {
              case Success(v) => Right(v)
              case Failure(_: IllegalArgumentException) => Left(s"Badly formatted UniqueIdentifier $value")
              case Failure(e) => throw e
            }
            case _ => Left("Bad uuid")
          }
        }

      def unbind(key: String, value: UniqueIdentifier) = stringBinder.unbind(key, value.toString())
    }

    implicit val resultPathBinder = new PathBindable.Parsing[Result](
      parse = (value: String) => Result(value),
      serialize = _.toString,
      error = (m: String, e: Exception) => "Can't parse %s as Result: %s".format(m, e.getMessage)
    )

    implicit val schemeIdPathBinder = new PathBindable.Parsing[SchemeId](
      parse = (value: String) => SchemeId(value),
      serialize = _.value,
      error = (m: String, e: Exception) => "Can't parse %s as SchemeId: %s".format(m, e.getMessage)
    )

    implicit val applicationStatusPathBinder = new PathBindable.Parsing[ApplicationStatus](
      parse = (value: String) => ApplicationStatus.withName(value),
      serialize = _.toString,
      error = (m: String, e: Exception) => "Can't parse %s as ApplicationStatus: %s".format(m, e.getMessage)
    )

    implicit val progressStatusPathBinder = new PathBindable.Parsing[ProgressStatus](
      parse = (value: String) => ProgressStatuses.nameToProgressStatus(value),
      serialize = _.toString,
      error = (m: String, e: Exception) => "Can't parse %s as ProgressStatus: %s".format(m, e.getMessage)
    )

    val pathDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    implicit val localDatePathBinder = new PathBindable.Parsing[LocalDate](
      parse = (dateVal: String) => LocalDate.parse(dateVal, pathDateFormat),
      serialize = _.toString(pathDateFormat),
      error = (m: String, e: Exception) => "Can't parse %s as LocalDate(%s): %s".format(m, pathDateFormat.toString, e.getMessage)
    )

    private def enumBinders[E <: Enumeration](enum: E) = {
      val error = (m: String, e: Exception) => "Can't parse %s as %s: %s".format(m, enum.getClass.getSimpleName, e.getMessage)
      (
        new QueryStringBindable.Parsing[E#Value](
          parse = enum.withName(_),
          serialize = _.toString,
          error = error
        ),
        new PathBindable.Parsing[E#Value](
          parse = enum.withName(_),
          serialize = _.toString,
          error = error
        )
      )
    }

    implicit val (eventTypeQueryBinder, eventTypePathBinder) = enumBinders(EventType)
    implicit val (skillTypeQueryBinder, skillTypePathBinder) = enumBinders(SkillType)
    implicit val (venueTypeQueryBinder, venueTypePathBinder) = enumBinders(VenueType)
    implicit val (allocationStatusQueryBinder, allocationStatusPathBinder) = enumBinders(model.AllocationStatuses)
  }
}
