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

import common.FutureEx
import model.SerialUpdateResult
import testkit.UnitSpec

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.time.Seconds

class SerialUpdateResultSpec extends UnitSpec {

  "Serial Update Result" must {
    "be constructed from an Either" in {

      val input = List(
        Left("fail"),
        Right("success")
      )

      val expected = SerialUpdateResult(failures = "fail" :: Nil, successes = "success" :: Nil)
      val actual = SerialUpdateResult.fromEither(input)

      actual mustBe expected
    }
  }

  "FutureEx utils" must {

    "construct a Left[T] from a future failed" in {
      val f = Future.failed(new Exception("Something went wrong"))
      val actual = FutureEx.futureToEither("blah", f).futureValue
      val expected = Left("blah")

      actual mustBe expected
    }

    "construct a Right[T] from a future success" in {
      val f = Future.successful(unit)
      val actual = FutureEx.futureToEither("blah", f).futureValue
      val expected = Right("blah")

      actual mustBe expected
    }
  }

}
