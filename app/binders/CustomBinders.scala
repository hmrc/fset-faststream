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

package binders

import models.UniqueIdentifier
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

}
