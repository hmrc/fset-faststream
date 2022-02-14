/*
 * Copyright 2022 HM Revenue & Customs
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

package services.evaluation

import model.EvaluationResults._
import model.SchemeId
import model.exchange.passmarksettings.{ AssessmentCentrePassMarkSettings, PassMarkThreshold }
import model.persisted.SchemeEvaluationResult
import play.api.Logging

trait AssessmentCentreAllSchemesEvaluator extends Logging {

  def evaluateSchemes(appId: String,
                      passmark: AssessmentCentrePassMarkSettings,
                      competencyAverages: CompetencyAverageResult,
                      schemes: Seq[SchemeId]): Seq[SchemeEvaluationResult] = {
    schemes.map { scheme =>
      val assessmentCentrePassMark = passmark.schemes.find { _.schemeId == scheme }
        .getOrElse(throw new IllegalStateException(s"Did not find assessment centre pass marks for scheme = $scheme, " +
          s"applicationId = $appId"))
      val makingEffectiveDecisionsResult = evaluateScore(appId, "makingEffectiveDecisions", competencyAverages.makingEffectiveDecisionsAverage,
        assessmentCentrePassMark.schemeThresholds.makingEffectiveDecisions)
      val workingTogetherDevelopingSelfAndOthersResult = evaluateScore(appId, "workingTogetherDevelopingSelfAndOthers",
        competencyAverages.workingTogetherDevelopingSelfAndOthersAverage,
        assessmentCentrePassMark.schemeThresholds.workingTogetherDevelopingSelfAndOthers)
      val communicatingAndInfluencingResult = evaluateScore(appId, "communicatingAndInfluencing",
        competencyAverages.communicatingAndInfluencingAverage,
        assessmentCentrePassMark.schemeThresholds.communicatingAndInfluencing)
      val seeingTheBigPictureResult = evaluateScore(appId, "seeingTheBigPicture", competencyAverages.seeingTheBigPictureAverage,
        assessmentCentrePassMark.schemeThresholds.seeingTheBigPicture)
      val overallResult = evaluateScore(appId, "overall", competencyAverages.overallScore, assessmentCentrePassMark.schemeThresholds.overall)

      SchemeEvaluationResult(scheme, combineTestResults(appId, scheme, makingEffectiveDecisionsResult,
        workingTogetherDevelopingSelfAndOthersResult, communicatingAndInfluencingResult, seeingTheBigPictureResult, overallResult).toString)
    }
  }

  private def evaluateScore(appId: String, name: String, score: Double, threshold: PassMarkThreshold): Result = {
    val result = if (score >= threshold.passThreshold) {
      Green
    }
    else if (score < threshold.failThreshold) {
      Red
    }
    else {
      Amber
    }
    logger.debug(s"[FSAC evaluate score] $appId - $name,score=$score,passMarks=$threshold,result=$result")
    result
  }

  private def combineTestResults(applicationId: String, scheme: SchemeId, results: Result*) = {
    require(results.nonEmpty, "Test results not found")
    val result = results match {
      case _ if results.contains(Red) => Red        // single red then red overall
      case _ if results.contains(Amber) => Amber    // single green then green overall
      case _ if results.forall(_ == Green) => Green // all green then green overall
    }
    logger.warn(s"[FSAC evaluator] - $applicationId combining results for $scheme: $results = $result")
    result
  }
}
