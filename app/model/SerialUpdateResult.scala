/*
 * Copyright 2018 HM Revenue & Customs
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

case class SerialUpdateResult[UpdateRequest](
  failures: Seq[UpdateRequest],
  successes: Seq[UpdateRequest]
) {

  import model.SerialUpdateResult.Result

  def result: Result.Value = (successes, failures) match {
    case (Nil, Nil) => Result.NO_OP
    case (Nil, _) => Result.FAILURE
    case (_, Nil) => Result.SUCCESS
    case (_, _) => Result.PARTIAL
  }
}

object SerialUpdateResult {

  object Result extends Enumeration {
    val SUCCESS, FAILURE, PARTIAL, NO_OP = Value
  }

  def fromEither[T](results: Seq[Either[T, T]]): SerialUpdateResult[T] = {

    val (f, s) = results.foldLeft(List.empty[T], List.empty[T]){ case (acc, res) =>
      val (failures, successes) = acc
      res match {
        case Left(l) => ( failures :+ l, successes)
        case Right(r) => (failures, successes :+ r)
      }
    }

    SerialUpdateResult(f, s)
  }
}
