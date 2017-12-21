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

import model.EvaluationResults.Green
import model.SchemeId
import model.exchange.passmarksettings.Phase1PassMarkSettings
import model.persisted.{ SchemeEvaluationResult, TestResult }
import play.api.Logger
import services.onlinetesting.OnlineTestResultsCalculator

trait Phase1TestEvaluation extends OnlineTestResultsCalculator {

  def evaluateForGis(schemes: List[SchemeId], sjqTestResult: TestResult,
                     passmark: Phase1PassMarkSettings): List[SchemeEvaluationResult] = {
    evaluate(isGis = true, schemes, passmark, sjqTestResult)
  }

  def evaluateForNonGis(schemes: List[SchemeId], sjqTestResult: TestResult, bqTestResult: TestResult,
                        passmark: Phase1PassMarkSettings): List[SchemeEvaluationResult] = {
    evaluate(isGis = false, schemes, passmark, sjqTestResult, Some(bqTestResult))
  }

  private def evaluate(isGis: Boolean, schemes: List[SchemeId], passmark: Phase1PassMarkSettings,
               sjqTestResult: TestResult, bqTestResultOpt: Option[TestResult] = None) = {
    for {
      schemeToEvaluate <- schemes
      schemePassmark <- passmark.schemes find (_.schemeId == schemeToEvaluate)
    } yield {
      val sjqResult = evaluateTestResult(schemePassmark.schemeThresholds.situational)(sjqTestResult.tScore)
      val bqResult = bqTestResultOpt.map(_.tScore).map(evaluateTestResult(schemePassmark.schemeThresholds.behavioural)).getOrElse(Green)
      Logger.info(s"**** Processing scheme $schemeToEvaluate, " +
        s"sjq score = ${sjqTestResult.tScore}, " +
        s"sjq fail = ${schemePassmark.schemeThresholds.situational.failThreshold}, " +
        s"sqj pass = ${schemePassmark.schemeThresholds.situational.passThreshold}, " +
        s"sqj result = $sjqResult, " +
        s"bq score = ${bqTestResultOpt.flatMap(_.tScore)}, " +
        s"bq fail = ${schemePassmark.schemeThresholds.behavioural.failThreshold}, " +
        s"bq pass = ${schemePassmark.schemeThresholds.behavioural.passThreshold}, " +
        s"bq result = $bqResult"
      )
      SchemeEvaluationResult(schemeToEvaluate, combineTestResults(sjqResult, bqResult).toString)
    }
  }
}
