/*
 * Copyright 2019 HM Revenue & Customs
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

import model.EvaluationResults.Result
import model.SchemeId
import model.exchange.passmarksettings.Phase2PassMarkSettings
import model.persisted.{ PsiTestResult, SchemeEvaluationResult }
import play.api.Logger
import services.onlinetesting.OnlineTestResultsCalculator2

trait Phase2TestEvaluation2 extends OnlineTestResultsCalculator2 {

  def evaluate(schemes: List[SchemeId], test1Result: PsiTestResult, test2Result: PsiTestResult,
               phase1SchemesEvaluation: List[SchemeEvaluationResult],
               passmark: Phase2PassMarkSettings): List[SchemeEvaluationResult] = {
    for {
      schemeToEvaluate <- schemes
      schemePassmark <- passmark.schemes find (_.schemeId == schemeToEvaluate)
      phase1SchemeEvaluation <- phase1SchemesEvaluation.find(_.schemeId == schemeToEvaluate)
    } yield {
      val p2Test1Result = evaluateTestResult(schemePassmark.schemeThresholds.test1)(test1Result.tScore)
      val p2Test2Result = evaluateTestResult(schemePassmark.schemeThresholds.test2)(test2Result.tScore)
      val phase1Result = Result(phase1SchemeEvaluation.result)
      Logger.debug(s"processing scheme $schemeToEvaluate, " +
        s"p1 result = $phase1Result, " +
        s"p2 test1 score = ${test1Result.tScore}, " +
        s"p2 test1 fail = ${schemePassmark.schemeThresholds.test1.failThreshold}, " +
        s"p2 test1 pass = ${schemePassmark.schemeThresholds.test1.passThreshold}, " +
        s"p2 test1 result = $p2Test1Result, " +
        s"p2 test2 score = ${test2Result.tScore}, " +
        s"p2 test2 fail = ${schemePassmark.schemeThresholds.test2.failThreshold}, " +
        s"p2 test2 pass = ${schemePassmark.schemeThresholds.test2.passThreshold}, " +
        s"p2 test2 result = $p2Test2Result")

      SchemeEvaluationResult(schemeToEvaluate, combineTestResults(schemeToEvaluate, phase1Result, p2Test1Result, p2Test2Result).toString)
    }
  }
}
