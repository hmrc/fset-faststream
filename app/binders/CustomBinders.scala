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

package binders

import model.UniqueIdentifier
import model.persisted.eventschedules.{ EventType, SkillType, VenueType }
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.SkillType.SkillType
import model.persisted.eventschedules.VenueType.VenueType
import play.api.mvc.{ PathBindable, QueryStringBindable }

import scala.util.{ Failure, Success, Try }

object CustomBinders {

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

  implicit def pathBindableEventType = new PathBindable[EventType] {
    def bind(key: String, value: String) =
      Try { EventType.withName(value) } match {
        case Success(v) => Right(v)
        case Failure(e: IllegalArgumentException) => Left("Badly formatted EventType '" + value + "'")
        case Failure(e) => throw e
      }
    def unbind(key: String, value: EventType) = value.toString()
  }

  implicit def pathBindableSkillType = new PathBindable[SkillType] {
    def bind(key: String, value: String) =
      Try { SkillType.withName(value) } match {
        case Success(v) => Right(v)
        case Failure(e: IllegalArgumentException) => Left("Badly formatted SkillType '" + value + "'")
        case Failure(e) => throw e
      }
    def unbind(key: String, value: SkillType) = value.toString()
  }

  implicit def pathBindableVenueType = new PathBindable[VenueType] {
    def bind(key: String, value: String) =
      Try { VenueType.withName(value) } match {
        case Success(v) => Right(v)
        case Failure(e: IllegalArgumentException) => Left("Badly formatted VenueType '" + value + "'")
        case Failure(e) => throw e
      }
    def unbind(key: String, value: VenueType) = value.toString()
  }
}
