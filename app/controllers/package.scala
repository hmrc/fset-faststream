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

import model.UniqueIdentifier
import model.persisted.eventschedules.{ EventType, SkillType, VenueType }
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.LocalDate
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import play.api.mvc.{ PathBindable, QueryStringBindable }

import scala.util.{ Failure, Success, Try }

package object controllers {

  object Binders {

    implicit def pathBindableIdentifier = new PathBindable[UniqueIdentifier] {
      def bind(key: String, value: String) =
        Try { UniqueIdentifier(value) } match {
          case Success(v) => Right(v)
          case Failure(e: IllegalArgumentException) => Left(s"Badly formatted UniqueIdentifier $value")
          case Failure(e) => throw e
        }
      def unbind(key: String, value: UniqueIdentifier) = value.toString
    }

    implicit def queryBindableIdentifier(implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[UniqueIdentifier] {
      def bind(key: String, params: Map[String, Seq[String]]) =
        for {
          uuid <- stringBinder.bind(key, params)
        } yield {
          uuid match {
            case Right(value) => Try { UniqueIdentifier(value) } match {
              case Success(v) => Right(v)
              case Failure(e: IllegalArgumentException) => Left(s"Badly formatted UniqueIdentifier $value")
              case Failure(e) => throw e
            }
            case _ => Left("Bad uuid")
          }
        }

      def unbind(key: String, value: UniqueIdentifier) = stringBinder.unbind(key, value.toString())
    }

    val pathDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

    implicit val localDatePathBinder = new PathBindable.Parsing[LocalDate](
      parse = (dateVal: String) => LocalDate.parse(dateVal, pathDateFormat),
      serialize = _.toString(pathDateFormat),
      error = (m: String, e: Exception) => "Can't parse %s as LocalDate(%s): %s".format(m, pathDateFormat.toString, e.getMessage)
    )

    /*implicit object skillTypeQueryBinder extends QueryStringBindable.Parsing[SkillType](
       parse = SkillType.withName(_),
       serialize = _.toString,
       error = (m: String, e: Exception) => "Can't parse %s as SchemeType : %s".format(m, e.getMessage)
    )*/
    private def enumQueryBinder[E <: Enumeration](enum: E) = {
      new QueryStringBindable.Parsing[E#Value](
        parse = enum.withName(_),
        serialize = _.toString,
        error = (m: String, e: Exception) => "Can't parse %s as %s: %s".format(m, enum.getClass.getSimpleName, e.getMessage)
      )
    }

    implicit val eventTypeQueryBinder = enumQueryBinder(EventType)
    implicit val skillTypeQueryBinder = enumQueryBinder(SkillType)
    implicit val venueTypeQueryBinder = enumQueryBinder(VenueType)

    private def enumPathBinder[E <: Enumeration](enum: E) = {
      new PathBindable.Parsing[E#Value](
        parse = enum.withName(_),
        serialize = _.toString,
        error = (m: String, e: Exception) => "Can't parse %s as %s: %s".format(m, enum.getClass.getSimpleName, e.getMessage)
      )
    }

    implicit val eventTypePathBinder = enumPathBinder(EventType)
    implicit val skillTypePathBinder = enumPathBinder(SkillType)
    implicit val venueTypePathBinder = enumPathBinder(VenueType)
  }
}
