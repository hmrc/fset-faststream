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

package services.onlinetesting.phase1

import model.EvaluationResults.Green
import model.{Phase, SchemeId}
import model.exchange.passmarksettings.Phase1PassMarkSettingsPersistence
import model.persisted.{PsiTestResult, SchemeEvaluationResult}
import services.onlinetesting.OnlineTestResultsCalculator

trait Phase1TestEvaluation extends OnlineTestResultsCalculator {

  def evaluateForGis(applicationId: String, schemes: List[SchemeId], test1Result: PsiTestResult, test2Result: PsiTestResult,
                     passmark: Phase1PassMarkSettingsPersistence): List[SchemeEvaluationResult] = {
    evaluate(applicationId, isGis = true, schemes, passmark, test1Result, Some(test2Result))
  }

  def evaluateForNonGis(applicationId: String, schemes: List[SchemeId], test1Result: PsiTestResult, test2Result: PsiTestResult,
                        passmark: Phase1PassMarkSettingsPersistence): List[SchemeEvaluationResult] = {
    evaluate(applicationId, isGis = false, schemes, passmark, test1Result, Some(test2Result))
  }

  private def evaluate(applicationId: String, isGis: Boolean, schemes: List[SchemeId], passmark: Phase1PassMarkSettingsPersistence,
                       test1Result: PsiTestResult, test2ResultOpt: Option[PsiTestResult] = None) = {
    for {
      schemeToEvaluate <- schemes
      schemePassmark <- passmark.schemes find (_.schemeId == schemeToEvaluate)
    } yield {
      val t1Result = evaluateTestResult(schemePassmark.schemeThresholds.test1)(test1Result.tScore)
      val t2Result = test2ResultOpt.map(_.tScore).map(evaluateTestResult(schemePassmark.schemeThresholds.test2)).getOrElse(Green)
      logger.warn(s"PHASE1 - appId $applicationId processing scheme $schemeToEvaluate, " +
        s"p1 test1 score = ${test1Result.tScore}, " +
        s"p1 test1 fail = ${schemePassmark.schemeThresholds.test1.failThreshold}, " +
        s"p1 test1 pass = ${schemePassmark.schemeThresholds.test1.passThreshold}, " +
        s"p1 test1 result = $t1Result, " +
        s"p1 test2 score = ${test2ResultOpt.map(_.tScore)}, " +
        s"p1 test2 fail = ${schemePassmark.schemeThresholds.test2.failThreshold}, " +
        s"p1 test2 pass = ${schemePassmark.schemeThresholds.test2.passThreshold}, " +
        s"p1 test2 result = $t2Result"
      )
      SchemeEvaluationResult(
        schemeToEvaluate,
        combineTestResults(applicationId, Phase.PHASE1, schemeToEvaluate, t1Result, t2Result).toString
      )
    }
  }
}
