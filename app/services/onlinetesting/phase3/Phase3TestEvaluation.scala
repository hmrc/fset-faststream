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

package services.onlinetesting.phase3

import connectors.launchpadgateway.exchangeobjects.in.reviewed.ReviewedCallbackRequest
import model.ApplicationRoute.ApplicationRoute
import model.EvaluationResults.{ Amber, Result }
import model.{ ApplicationRoute, Scheme, SchemeId }
import model.exchange.passmarksettings.Phase3PassMarkSettings
import model.persisted.SchemeEvaluationResult
import play.api.Logger
import services.onlinetesting.OnlineTestResultsCalculator

trait Phase3TestEvaluation extends OnlineTestResultsCalculator {

  def evaluate(applicationRoute: ApplicationRoute, schemes: List[SchemeId], launchpadTestResult: ReviewedCallbackRequest,
               phase2SchemesEvaluation: List[SchemeEvaluationResult],
               passmark: Phase3PassMarkSettings): List[SchemeEvaluationResult] = {

    val evaluationResults = for {
      schemeToEvaluate <- schemes
    } yield {
      val schemePassmarkOpt = passmark.schemes find (_.schemeId == schemeToEvaluate)
      phase2SchemesEvaluation.find(_.schemeId == schemeToEvaluate).flatMap { phase2SchemeEvaluation =>
        schemePassmarkOpt.map { schemePassmark =>
          val phase3Result = evaluateTestResult(schemePassmark.schemeThresholds.videoInterview)(
            Some(launchpadTestResult.calculateTotalScore()))
          Logger.debug(s"processing scheme $schemeToEvaluate, " +
            s"video score = ${launchpadTestResult.calculateTotalScore()}, " +
            s"video fail = ${schemePassmark.schemeThresholds.videoInterview.failThreshold}, " +
            s"video pass = ${schemePassmark.schemeThresholds.videoInterview.passThreshold}, " +
            s"video result = $phase3Result")
          val phase2Result = Result(phase2SchemeEvaluation.result)
          Some(SchemeEvaluationResult(schemeToEvaluate, combineTestResults(phase2Result, phase3Result).toString))
        } getOrElse {
          if (Scheme.isSdip(schemeToEvaluate) && applicationRoute == ApplicationRoute.SdipFaststream) {
            val phase2Result = Result(phase2SchemeEvaluation.result)
            Option(SchemeEvaluationResult(schemeToEvaluate, combineTestResults(phase2Result, Amber).toString))
          } else {
            None
          }
        }
      }
    }
    evaluationResults.flatten
  }
}
