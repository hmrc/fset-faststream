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

package testkit

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import org.scalatestplus.play.PlaySpec

import scala.concurrent.Future

trait FutureHelper {
  this: PlaySpec with ScalaFutures =>

  def assertNoExceptions(future: Future[Unit]) = try {
    implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))
    future.futureValue
  } catch {
    case e: Throwable => fail(e)
  }

  def emptyFuture = Future.successful(())
}
