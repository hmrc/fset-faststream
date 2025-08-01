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

package common

import play.api.Logging

import scala.collection.BuildFrom
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

object FutureEx extends Logging {
  /**
   * Serial alternative to Future.traverse. Transforms a `TraversableOnce[A]` into a `Future[TraversableOnce[B]]` using
   * the provided function `A => Future[B]`. This is useful for performing a serial map. For example, to apply a function
   * to all items of a list in serial:
   *
   *  {{{
   *    val myFutureList = FutureEx.traverseSerial(myList)(x => Future(myFunc(x)))
   *  }}}
   */
  def traverseSerial[A, B, M[X] <: IterableOnce[X]](
    in: M[A]
  )(fn: A => Future[B])(implicit bf: BuildFrom[M[A], B, M[B]], executor: ExecutionContext): Future[M[B]] =
    in.iterator.foldLeft(Future.successful(bf.newBuilder(in))) { (previousFuture, a) =>
      for { previousResult <- previousFuture
            newFutureResult <- fn(a)
      } yield previousResult += newFutureResult
    }.map(_.result())

  /**
    * Create futures of Try[B] so that filtering/processing can be carried out later
    * http://stackoverflow.com/questions/15775824/how-to-carry-on-executing-future-sequence-despite-failure
    */
  def traverseToTry[A, B](seq: Seq[A])(f: A => Future[B])(implicit executor: ExecutionContext): Future[Seq[Try[B]]] = {
    // Can also be done more concisely (but less efficiently) as:
    // f.map(Success(_)).recover{ case t: Throwable => Failure( t ) }
    // NOTE: you might also want to move this into an enrichment class
    def mapValue[T]( f: Future[T] ): Future[Try[T]] = {
      val prom = Promise[Try[T]]()
      f onComplete prom.success
      prom.future
    }

    Future.traverse( seq )( f andThen mapValue )
  }

  def futureToEither[T](updateReq: T, result: Future[Unit])(implicit ex: ExecutionContext): Future[Either[T, T]] = {
    result.map { _ => Right(updateReq) }.recover { case _: Exception => Left(updateReq) }
  }

  def withErrLogging[T](logPrefix: String)(f: Future[T])(implicit ec: ExecutionContext): Future[T] = {
    f.recoverWith { case ex => logger.warn(s"$logPrefix: ${ex.getMessage}"); f }
  }
}

object TryEx {
   def traverseSerial[A, B, M[X] <: IterableOnce[X]](in: M[A])(fn: A => Try[B])
     (implicit bf: BuildFrom[M[A], B, M[B]], executor: ExecutionContext): Try[M[B]] =
     in.iterator.foldLeft(Try(bf.newBuilder(in))) { (previous, a) =>
      for { previousResult <- previous
            newResult <- fn(a)
      } yield previousResult += newResult
    }.map(_.result())
}
