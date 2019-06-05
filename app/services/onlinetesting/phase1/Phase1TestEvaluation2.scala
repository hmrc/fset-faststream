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

package services.onlinetesting.phase1

import model.EvaluationResults.Green
import model.SchemeId
import model.exchange.passmarksettings.Phase1PassMarkSettings
import model.persisted.{ PsiTestResult, SchemeEvaluationResult }
import play.api.Logger
import services.onlinetesting.OnlineTestResultsCalculator2

trait Phase1TestEvaluation2 extends OnlineTestResultsCalculator2 {

  def evaluateForGis(schemes: List[SchemeId], test1Result: PsiTestResult, test4Result: PsiTestResult,
                     passmark: Phase1PassMarkSettings): List[SchemeEvaluationResult] = {
    evaluate(isGis = true, schemes, passmark, test1Result, None, None, test4Result)
  }

  def evaluateForNonGis(schemes: List[SchemeId], test1Result: PsiTestResult, test2Result: PsiTestResult,
                        test3Result: PsiTestResult, test4Result: PsiTestResult,
                        passmark: Phase1PassMarkSettings): List[SchemeEvaluationResult] = {
    evaluate(isGis = false, schemes, passmark, test1Result, Some(test2Result), Some(test3Result), test4Result)
  }

  private def evaluate(isGis: Boolean, schemes: List[SchemeId], passmark: Phase1PassMarkSettings,
                       test1Result: PsiTestResult, test2ResultOpt: Option[PsiTestResult] = None,
                       test3ResultOpt: Option[PsiTestResult] = None, test4Result: PsiTestResult) = {
    for {
      schemeToEvaluate <- schemes
      schemePassmark <- passmark.schemes find (_.schemeId == schemeToEvaluate)
    } yield {
      val t1Result = evaluateTestResult(schemePassmark.schemeThresholds.situational)(test1Result.tScore)
      val t2Result = test2ResultOpt.map(_.tScore).map(evaluateTestResult(schemePassmark.schemeThresholds.behavioural)).getOrElse(Green)
      val t3Result = test3ResultOpt.map(_.tScore).map(evaluateTestResult(schemePassmark.schemeThresholds.behavioural)).getOrElse(Green)
      val t4Result = evaluateTestResult(schemePassmark.schemeThresholds.situational)(test4Result.tScore)
      Logger.info(s"Processing scheme $schemeToEvaluate, " +
        s"test1 score = ${test1Result.tScore}, " +
        s"test1 fail = ${schemePassmark.schemeThresholds.situational.failThreshold}, " +
        s"test1 pass = ${schemePassmark.schemeThresholds.situational.passThreshold}, " +
        s"test1 result = $t1Result, " +
        s"test2 score = ${test2ResultOpt.map(_.tScore)}, " +
        s"test2 fail = ${schemePassmark.schemeThresholds.behavioural.failThreshold}, " +
        s"test2 pass = ${schemePassmark.schemeThresholds.behavioural.passThreshold}, " +
        s"test2 result = $t2Result, " +
        s"test3 score = ${test3ResultOpt.map(_.tScore)}, " +
        s"test3 fail = ${schemePassmark.schemeThresholds.behavioural.failThreshold}, " +
        s"test3 pass = ${schemePassmark.schemeThresholds.behavioural.passThreshold}, " +
        s"test3 result = $t3Result, " +
        s"test4 score = ${test4Result.tScore}, " +
        s"test4 fail = ${schemePassmark.schemeThresholds.behavioural.failThreshold}, " +
        s"test4 pass = ${schemePassmark.schemeThresholds.behavioural.passThreshold}, " +
        s"test4 result = $t4Result"
      )
      SchemeEvaluationResult(schemeToEvaluate, combineTestResults(t1Result, t2Result, t3Result, t4Result).toString)
    }
  }
}
