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

import model.EvaluationResults._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import testkit.UnitSpec

class EvaluationResultsSpec extends UnitSpec with TableDrivenPropertyChecks {

  "Evaluation results" must {

    "be returned for a string value" in {
      EvaluationResults.Result.apply("Green") mustBe Green
      EvaluationResults.Result.apply("Amber") mustBe Amber
      EvaluationResults.Result.apply("Red") mustBe Red
      EvaluationResults.Result.apply("Withdrawn") mustBe Withdrawn
    }

    "add withdrawn" in {
      val table = Table(
        ("addend", "sum"),
        (Red, Withdrawn),
        (Amber, Withdrawn),
        (Green, Withdrawn),
        (Withdrawn, Withdrawn)
      )

      forAll (table) { (addend: Result, sum: Result) =>
        Withdrawn + addend mustBe sum
      }
    }

    "add red" in {
      val table = Table(
        ("addend", "sum"),
        (Red, Red),
        (Amber, Red),
        (Green, Red),
        (Withdrawn, Withdrawn)
      )

      forAll (table) { (addend: Result, sum: Result) =>
        Red + addend mustBe sum
      }
    }

    "add amber" in {
      val table = Table(
        ("addend", "sum"),
        (Red, Red),
        (Amber, Amber),
        (Green, Green),
        (Withdrawn, Withdrawn)
      )

      forAll (table) { (addend: Result, sum: Result) =>
        Amber + addend mustBe sum
      }
    }

    "add Green" in {
      val table = Table(
        ("addend", "sum"),
        (Red, Red),
        (Amber, Amber),
        (Green, Green),
        (Withdrawn, Withdrawn)
      )

      forAll (table) { (addend: Result, sum: Result) =>
        Green + addend mustBe sum
      }
    }
  }

  "EvaluationResults.Result.fromPassFail" must {
    "return Green for Pass" in {
     EvaluationResults.Result.fromPassFail("Pass") mustBe Green
    }

    "return Red for Fail" in {
      EvaluationResults.Result.fromPassFail("Fail") mustBe Red
    }
  }

}
