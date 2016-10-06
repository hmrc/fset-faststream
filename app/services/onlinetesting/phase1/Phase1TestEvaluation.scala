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

package services.onlinetesting.phase1

import model.EvaluationResults.{ Amber, Green, Red, Result }
import model.SchemeType._
import model.exchange.passmarksettings.Phase1PassMarkSettings
import model.persisted.{ SchemeEvaluationResult, TestResult }

trait Phase1TestEvaluation {

  def evaluateForGis(schemes: List[SchemeType], sjqTestResult: TestResult, passmark: Phase1PassMarkSettings) = {
    schemes map { scheme =>
      val result = evaluateResultsForExercise(sjqTestResult, scheme, passmark)
      SchemeEvaluationResult(scheme, result.toString)
    }
  }

  def evaluateForNonGis(schemes: List[SchemeType], sjqTestResult: TestResult, bqTestResult: TestResult, passmark: Phase1PassMarkSettings) = {
    schemes map { scheme =>
      val sjqResult = evaluateResultsForExercise(sjqTestResult, scheme, passmark)
      val bqResult = evaluateResultsForExercise(bqTestResult, scheme, passmark)
      // TODO do the math here
      val result = (sjqResult, bqResult) match {
        case (Red, _) => Red
        case (_, Red) => Red
        case (Amber, _) => Amber
        case (_, Amber) => Amber
        case (Green, Green) => Green
      }

      SchemeEvaluationResult(scheme, result.toString)
    }
  }

  private def evaluateResultsForExercise(testResult: TestResult, scheme: SchemeType, passmarkSettings: Any): Result = {
    val tScore = testResult.tScore.get
    // TODO Integrate with Passmark
    val failmark = 20.0
    val passmark = 80.0
    determineResult(tScore, failmark, passmark)
  }

  private def determineResult(tScore: Double, failmark: Double, passmark: Double): Result = {
    def determineResultWithoutAmbers = {
      if (tScore <= failmark) {
        Red
      } else if (tScore >= passmark) {
        Green
      } else {
        Amber
      }
    }

    def determineResultWithAmbers = {
      if (tScore >= passmark) {
        Green
      } else {
        Red
      }
    }

    val isAmberGapPresent = failmark < passmark
    if (isAmberGapPresent) {
      determineResultWithAmbers
    } else {
      determineResultWithoutAmbers
    }
  }
}
