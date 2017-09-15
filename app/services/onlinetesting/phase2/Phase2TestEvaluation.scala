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

package services.onlinetesting.phase2

import model.ApplicationRoute.ApplicationRoute
import model.EvaluationResults.{ Amber, Result }
import model.{ ApplicationRoute, Scheme, SchemeId }
import model.exchange.passmarksettings.Phase2PassMarkSettings
import model.persisted.{ SchemeEvaluationResult, TestResult }
import play.api.Logger
import services.onlinetesting.OnlineTestResultsCalculator

trait Phase2TestEvaluation extends OnlineTestResultsCalculator {

  def evaluate(applicationRoute: ApplicationRoute, schemes: List[SchemeId], etrayTestResult: TestResult,
               phase1SchemesEvaluation: List[SchemeEvaluationResult], passmark: Phase2PassMarkSettings): List[SchemeEvaluationResult] = {

    val evaluationResults = for {
      schemeToEvaluate <- schemes
    } yield {
      val schemePassmarkOpt = passmark.schemes.find(_.schemeId == schemeToEvaluate)
      phase1SchemesEvaluation.find(_.schemeId == schemeToEvaluate).flatMap { phase1SchemeEvaluation =>
        schemePassmarkOpt.map { schemePassmark =>
          val phase2Result = evaluateTestResult(schemePassmark.schemeThresholds.etray)(etrayTestResult.tScore)
          Logger.debug(s"processing scheme $schemeToEvaluate, " +
            s"etray score = ${etrayTestResult.tScore}, " +
            s"etray fail = ${schemePassmark.schemeThresholds.etray.failThreshold}, " +
            s"etray pass = ${schemePassmark.schemeThresholds.etray.passThreshold}, " +
            s"etray result = $phase2Result")
          val phase1Result = Result(phase1SchemeEvaluation.result)
          Option(SchemeEvaluationResult(schemeToEvaluate, combineTestResults(phase1Result, phase2Result).toString))
        } getOrElse {
          if (Scheme.isSdip(schemeToEvaluate) && applicationRoute == ApplicationRoute.SdipFaststream) {
            val phase1Result = Result(phase1SchemeEvaluation.result)
            Option(SchemeEvaluationResult(schemeToEvaluate, combineTestResults(phase1Result, Amber).toString))
          } else {
            None
          }
        }
      }
    }
    evaluationResults.flatten
  }
}
