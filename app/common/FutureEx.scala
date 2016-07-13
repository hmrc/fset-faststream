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

package common

import scala.collection.generic.CanBuildFrom
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.higherKinds

object FutureEx {
  /**
   * Serial alternative to Future.traverse. Transforms a `TraversableOnce[A]` into a `Future[TraversableOnce[B]]` using
   * the provided function `A => Future[B]`. This is useful for performing a serial map. For example, to apply a function
   * to all items of a list in serial:
   *
   *  {{{
   *    val myFutureList = FutureEx.traverseSerial(myList)(x => Future(myFunc(x)))
   *  }}}
   */
  def traverseSerial[A, B, M[X] <: TraversableOnce[X]](
    in: M[A]
  )(fn: A => Future[B])(implicit cbf: CanBuildFrom[M[A], B, M[B]], executor: ExecutionContext): Future[M[B]] =
    in.foldLeft(Future.successful(cbf(in))) { (fr, a) =>
      for (r <- fr; b <- fn(a)) yield r += b
    }.map(_.result())
}
