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
import model.exchange.passmarksettings.{ PassMarkThreshold, Phase1PassMarkSettings, Phase1PassMarkThresholds }
import model.persisted.{ SchemeEvaluationResult, TestResult }

trait Phase1TestEvaluation {

  def evaluateForGis(schemes: List[SchemeType], sjqTestResult: TestResult,
                     passmark: Phase1PassMarkSettings): List[SchemeEvaluationResult] = {
    evaluate(isGis = true, schemes, passmark, sjqTestResult)
  }

  def evaluateForNonGis(schemes: List[SchemeType], sjqTestResult: TestResult, bqTestResult: TestResult,
                        passmark: Phase1PassMarkSettings): List[SchemeEvaluationResult] = {
    evaluate(isGis = false, schemes, passmark, sjqTestResult, Some(bqTestResult))
  }

  private def evaluate(isGis: Boolean, schemes: List[SchemeType], passmark: Phase1PassMarkSettings,
               sjqTestResult: TestResult, bqTestResultOpt: Option[TestResult] = None) = {
    for {
      schemeToEvaluate <- schemes
      schemePassmarkOpt = passmark.schemes find (_.schemeName == schemeToEvaluate)
      schemePassmark <- schemePassmarkOpt
    } yield {
      val sjqResult = evaluateResultsForExercise(schemePassmark.schemeThresholds.situational)(sjqTestResult)
      val bqResult = bqTestResultOpt.map(evaluateResultsForExercise(schemePassmark.schemeThresholds.behavioural)).getOrElse(Green)

      val result = (sjqResult, bqResult) match {
        case (Red, _) => Red
        case (_, Red) => Red
        case (Amber, _) => Amber
        case (_, Amber) => Amber
        case (Green, Green) => Green
      }

      SchemeEvaluationResult(schemeToEvaluate, result.toString)
    }
  }

  private def evaluateResultsForExercise(threshold: PassMarkThreshold)(testResult: TestResult): Result = {
    val tScore = testResult.tScore.get
    val failmark = threshold.failThreshold
    val passmark = threshold.passThreshold

    if (tScore >= passmark) {
      Green
    } else if (tScore <= failmark) {
      Red
    } else {
      Amber
    }
  }
}
