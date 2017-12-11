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

package services.evaluation

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.AssessmentPassMarksSchemesAndScores
import model.EvaluationResults._
import model.persisted.SchemeEvaluationResult
import play.api.Logger

trait AssessmentCentreEvaluationEngine {

  def evaluate(candidateScores: AssessmentPassMarksSchemesAndScores,
    config: AssessmentEvaluationMinimumCompetencyLevel): AssessmentEvaluationResult
}

object AssessmentCentreEvaluationEngine extends AssessmentCentreEvaluationEngine with AssessmentScoreCalculator
  with AssessmentCentreAllSchemesEvaluator {

  def evaluate(candidateScores: AssessmentPassMarksSchemesAndScores,
    mclConfig: AssessmentEvaluationMinimumCompetencyLevel): AssessmentEvaluationResult = {
    val appId = candidateScores.scores.applicationId
    val competencyAverage = countAverage(candidateScores.scores)
    Logger.debug(s"CompetencyAverage = $competencyAverage - applicationId = $appId")
    val passedMinimumCompetencyLevelCheckOpt = passMinimumCompetencyLevel(competencyAverage, mclConfig)


    passedMinimumCompetencyLevelCheckOpt match {
      case Some(false) =>
        Logger.debug(s"Minimum competency level check failed - mcl = ${mclConfig.minimumCompetencyLevelScore}, applicationId = $appId")
        val allSchemesRed = candidateScores.schemes.map(s => SchemeEvaluationResult(s, Red.toString))
        AssessmentEvaluationResult(passedMinimumCompetencyLevelCheckOpt, competencyAverage, allSchemesRed)
      case _ =>
        Logger.debug(s"Minimum competency level check passed - mcl = $mclConfig, applicationId = $appId")

        val evaluationResult = evaluateSchemes(appId.toString(), candidateScores.passmark,
          competencyAverage.overallScore, candidateScores.schemes)
        AssessmentEvaluationResult(passedMinimumCompetencyLevelCheckOpt, competencyAverage, evaluationResult)
    }
  }

  private def passMinimumCompetencyLevel(competencyAverage: CompetencyAverageResult,
    config: AssessmentEvaluationMinimumCompetencyLevel): Option[Boolean] = {
    require(competencyAverage.competencyAverageScores.nonEmpty, "Competency scores to check against MCL cannot be empty")

    val result = for {
      mclScore <- config.minimumCompetencyLevelScore if config.enabled
    } yield {
      val allCompetencyAveragesPassedMclCheck = competencyAverage.competencyAverageScores.forall(_ >= mclScore)
      allCompetencyAveragesPassedMclCheck
    }

    require(!config.enabled || result.nonEmpty, "Cannot check minimum competency level for assessment")
    result
  }
}
