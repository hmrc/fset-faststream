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

package services.onlinetesting.phase3

import connectors.launchpadgateway.exchangeobjects.in.reviewed.ReviewedCallbackRequest
import model.EvaluationResults.Result
import model.SchemeType._
import model.exchange.passmarksettings.Phase3PassMarkSettings
import model.persisted.SchemeEvaluationResult
import services.onlinetesting.OnlineTestResultsCalculator

trait Phase3TestEvaluation extends OnlineTestResultsCalculator {

  def evaluate(schemes: List[SchemeType], launchpadTestResult: ReviewedCallbackRequest,
               phase2SchemesEvaluation: List[SchemeEvaluationResult],
               passmark: Phase3PassMarkSettings): List[SchemeEvaluationResult] = {
    for {
      schemeToEvaluate <- schemes
      schemePassmarkOpt = passmark.schemes find (_.schemeName == schemeToEvaluate)
      schemePassmark <- schemePassmarkOpt
      phase2SchemeEvaluation <- phase2SchemesEvaluation.find(_.scheme == schemeToEvaluate)
    } yield {
      val Phase3Result = evaluateTestResult(schemePassmark.schemeThresholds.videoInterview)(
        Some(launchpadTestResult.calculateTotalScore()))
      val phase2Result = Result(phase2SchemeEvaluation.result)
      SchemeEvaluationResult(schemeToEvaluate, combineTestResults(phase2Result, Phase3Result).toString)
    }
  }
}
