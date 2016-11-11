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

package services.onlinetesting.phase2

import model.EvaluationResults.{ Amber, Green, Red, Result }
import model.SchemeType._
import model.exchange.passmarksettings.{ PassMarkThreshold, Phase2PassMarkSettings }
import model.persisted.{ SchemeEvaluationResult, TestResult }

trait Phase2TestEvaluation {

  def evaluate(schemes: List[SchemeType], etrayTestResult: TestResult,
               phase1SchemesEvaluation: List[SchemeEvaluationResult],
               passmark: Phase2PassMarkSettings): List[SchemeEvaluationResult] = {
    for {
      schemeToEvaluate <- schemes
      schemePassmarkOpt = passmark.schemes find (_.schemeName == schemeToEvaluate)
      schemePassmark <- schemePassmarkOpt
      phase1SchemeEvaluation <- phase1SchemesEvaluation.find(_.scheme == schemeToEvaluate)
    } yield {
      val phase2Result = evaluateResultsForExercise(schemePassmark.schemeThresholds.etray)(etrayTestResult)
      val phase1Result = Result(phase1SchemeEvaluation.result)
      val result = (phase2Result, phase1Result) match {
        case (Green, Green) => Green
        case (Amber, Green) => Amber
        case (Green, Amber) => Amber
        case (Red, _) => Red
        case (_, Red) => Red
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
