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

package model


import scala.concurrent.{ ExecutionContext, Future }

case class SerialUpdateResult[UpdateRequest](
  failures: Seq[UpdateRequest],
  successes: Seq[UpdateRequest]
) {

  import model.SerialUpdateResult.Result

  def result: Result.Value = (successes, failures) match {
    case (Nil, _) => Result.FAILURE
    case (_, Nil) => Result.SUCCESS
    case (_, _) => Result.PARTIAL
  }

}

object SerialUpdateResult {

  object Result extends Enumeration {
    val SUCCESS, FAILURE, PARTIAL = Value
  }

  def fromEither[T](results: Seq[Either[T, T]]): SerialUpdateResult[T] = {

    results.foldLeft(List.empty[T], List.empty[T]){ case (acc, res) =>
      res match {
        case Left(l) => (acc._1 :+ l, acc._2)
        case Right(r) => (acc._1, acc._2 :+ r)
      }
    } match {
      case (f, Nil) => SerialUpdateResult(f, Nil)
      case (Nil, s) => SerialUpdateResult(Nil, s)
      case (f, s) => SerialUpdateResult(f, s)
    }
  }

  def futureToEither[T](updateReq: T, result: Future[Unit])(implicit ex: ExecutionContext): Future[Either[T, T]] = {
    result.map ( _ => Right(updateReq))
      .recover {
        case e: Exception => play.api.Logger.error(e.getMessage)
          Left(updateReq)
      }
  }
}
