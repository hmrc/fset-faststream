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

package services.onlinetesting.phase1

import model.EvaluationResults._
import model.Phase1TestExamples._
import model.SchemeType._
import model.exchange.passmarksettings._
import model.persisted.SchemeEvaluationResult
import org.scalatest.prop.TableDrivenPropertyChecks
import services.BaseServiceSpec

class Phase1TestEvaluationSpec extends BaseServiceSpec with TableDrivenPropertyChecks {
  val evaluation = new Phase1TestEvaluation {}

  val CurrentPassmarkWithAmbers = Phase1PassMarkSettingsExamples.passmark.copy(schemes = List(
    passmark(Commercial, sjqFail = 20.0, sjqPass = 80.0, bqFail = 10.0, bqPass = 90.0)
  ))
  val CurrentPassmarkWithoutAmbers = Phase1PassMarkSettingsExamples.passmark.copy(schemes = List(
    passmark(Generalist, sjqFail = 45.0, sjqPass = 45.0, bqFail = 45.0, bqPass = 45.0)
  ))

  // format: OFF
  // None in 'bq result' means GIS application
  val Phase1EvaluationData = Table(
    ("schemes",           "sjq result",            "bq result",               "result"),
    (List(Commercial),    10.0,                     Some(90.0),                List(Red)),   // sjq = R, bq = G = R
    (List(Commercial),    20.0,                     Some(90.0),                List(Amber)), // sjq = A, bq = G = A
    (List(Commercial),    30.0,                     Some(10.0),                List(Amber)), // sjq = A, bq = A = A
    (List(Commercial),    0.0,                      Some(0.0),                 List(Red)),   // sjq = R, bq = R = R
    (List(Commercial),    20.01,                    Some(100.0),               List(Amber)), // sjq = A, bq = G = A
    (List(Commercial),    100.0,                    Some(10.00001),            List(Amber)), // sjq = G, bq = A = A
    (List(Commercial),    80.0,                     Some(90.0),                List(Green)), // sjq = G, bq = G = G
    (List(Commercial),    100.0,                    Some(100.0),               List(Green)), // sjq = G, bq = G = G
    (List(Commercial),    19.99,                    None,                      List(Red)),   // sjq = R, bq = - = R
    (List(Commercial),    20.0,                     None,                      List(Amber)), // sjq = A, bq = - = A
    (List(Commercial),    25.0,                     None,                      List(Amber)), // sjq = A, bq = - = A
    (List(Commercial),    80.0,                     None,                      List(Green)), // sjq = G, bq = - = G
    (List(Commercial),    85.0,                     None,                      List(Green)), // sjq = G, bq = - = G
    // Edip passmark does not exist, hence the evaluation must be skipped
    (List(Edip),          85.0,                     None,                      List()),
    (List(Edip),          85.0,                     Some(75.0),                List())
  )

  val Phase1EvaluationDataWithoutAmbers = Table(
    ("schemes",           "sjq result",            "bq result",               "result"),
    (List(Generalist),    45.0,                     None,                      List(Green)), // sjq = G, bq = - = G
    (List(Generalist),    45.0,                     Some(45.0),                List(Green)), // sjq = G, bq = G = G
    (List(Generalist),    10.0,                     Some(90.0),                List(Red)),   // sjq = R, bq = G = R
    (List(Generalist),    20.0,                     Some(90.0),                List(Red)),   // sjq = R, bq = G = R
    (List(Generalist),    30.0,                     Some(10.0),                List(Red)),   // sjq = R, bq = R = R
    (List(Generalist),    0.0,                      Some(0.0),                 List(Red)),   // sjq = R, bq = R = R
    (List(Generalist),    20.01,                    Some(100.0),               List(Red)),   // sjq = R, bq = G = R
    (List(Generalist),    100.0,                    Some(10.00001),            List(Red)),   // sjq = G, bq = R = R
    (List(Generalist),    80.0,                     Some(90.0),                List(Green)), // sjq = G, bq = G = G
    (List(Generalist),    100.0,                    Some(100.0),               List(Green)), // sjq = G, bq = G = G
    (List(Generalist),    20.0,                     None,                      List(Red)),   // sjq = R, bq = - = R
    (List(Generalist),    25.0,                     None,                      List(Red)),   // sjq = R, bq = - = R
    (List(Generalist),    80.0,                     None,                      List(Green)), // sjq = G, bq = - = G
    (List(Generalist),    85.0,                     None,                      List(Green)), // sjq = G, bq = - = G
    (List(Generalist),    45.0,                     None,                      List(Green))  // sjq = G, bq = - = G
  )
  // format: ON

  "evaluate phase1 tests" should {
    "evaluate schemes for Passmark with AMBER gap" in {
      forAll(Phase1EvaluationData) { (schemes: List[SchemeType], sjqResult, bqResultOpt: Option[Double], expected: List[Result]) =>
        val result = bqResultOpt match {
          case Some(bqResult) =>
            evaluation.evaluateForNonGis(schemes, createTestResult(sjqResult), createTestResult(bqResult), CurrentPassmarkWithAmbers)
          case None =>
            evaluation.evaluateForGis(schemes, createTestResult(sjqResult), CurrentPassmarkWithAmbers)
        }

        result mustBe normalize(schemes, expected)
      }
    }

    "evaluate schemes for Passmark without AMBER gap" in {
      forAll(Phase1EvaluationDataWithoutAmbers) { (schemes: List[SchemeType], sjqResult, bqResultOpt: Option[Double], expected: List[Result]) =>
        val result = bqResultOpt match {
          case Some(bqResult) =>
            evaluation.evaluateForNonGis(schemes, createTestResult(sjqResult), createTestResult(bqResult), CurrentPassmarkWithoutAmbers)
          case None =>
            evaluation.evaluateForGis(schemes, createTestResult(sjqResult), CurrentPassmarkWithoutAmbers)
        }

        result mustBe normalize(schemes, expected)
      }
    }
  }

  def normalize(schemes: List[SchemeType], expected: List[Result]) = {
    schemes.zip(expected).map { case (s, r) => SchemeEvaluationResult(s, r.toString) }
  }

  def passmark(s: SchemeType, sjqFail: Double, sjqPass: Double, bqFail: Double, bqPass: Double) = {
    Phase1PassMark(s, Phase1PassMarkThresholds(PassMarkThreshold(sjqFail, sjqPass), PassMarkThreshold(bqFail, bqPass)))
  }
}
