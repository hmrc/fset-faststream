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
import model.AssessmentEvaluationCommands.AssessmentPassmarkPreferencesAndScores
import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.EvaluationResults._
import model.PassmarkPersistedObjects.PassMarkSchemeThreshold
import play.api.Logger

trait AssessmentCentrePassmarkRulesEngine {

  def evaluate(
    candidateScore: AssessmentPassmarkPreferencesAndScores,
    config: AssessmentEvaluationMinimumCompetencyLevel
  ): AssessmentRuleCategoryResult

}

object AssessmentCentrePassmarkRulesEngine extends AssessmentCentrePassmarkRulesEngine with AssessmentScoreCalculator {

  def evaluate(
    candidateScore: AssessmentPassmarkPreferencesAndScores,
    config: AssessmentEvaluationMinimumCompetencyLevel
  ): AssessmentRuleCategoryResult = {
    def passMinimumCompetencyLevel(
      competencyAverage: CompetencyAverageResult,
      config: AssessmentEvaluationMinimumCompetencyLevel
    ): Option[Boolean] = {
      if (config.enabled) {
        val minCompetencyLevelScore = config.minimumCompetencyLevelScore
          .getOrElse(throw new IllegalStateException("Competency level not set"))
        val minMotivationalFitScore = config.motivationalFitMinimumCompetencyLevelScore
          .getOrElse(throw new IllegalStateException("Motivational Fit competency level not set"))

        Some(competencyAverage.scoresWithWeightOne.forall(_ >= minCompetencyLevelScore) &&
          competencyAverage.scoresWithWeightTwo.forall(_ >= minMotivationalFitScore))
      } else {
        None
      }
    }

    val competencyAverage = countScores(candidateScore.scores)
    val passedMinimumCompetencyLevelOpt = passMinimumCompetencyLevel(competencyAverage, config)

    if (passedMinimumCompetencyLevelOpt.getOrElse(true)) {
      evaluatePassmark(competencyAverage, candidateScore).copy(passedMinimumCompetencyLevel = passedMinimumCompetencyLevelOpt)
    } else {
      AssessmentRuleCategoryResult(passedMinimumCompetencyLevelOpt, None, None, None, None, None, None, None)
    }
  }

  //scalastyle:off
  private def evaluatePassmark(competencyAverage: CompetencyAverageResult, candidateScores: AssessmentPassmarkPreferencesAndScores): AssessmentRuleCategoryResult = {
    val preferences = candidateScores.preferences
    val location1Scheme1 = preferences.firstLocation.firstFramework
    val location1Scheme2 = preferences.firstLocation.secondFramework
    val location2Scheme1 = preferences.secondLocation.map(s => s.firstFramework)
    val location2Scheme2 = preferences.secondLocation.flatMap(s => s.secondFramework)
    val alternativeScheme = preferences.alternatives.map(_.framework)

    val overallScore = competencyAverage.overallScore
    Logger.debug(s"Application ${candidateScores.scores.applicationId} overall score: $overallScore")
    val passmark = candidateScores.passmark

    val allSchemesEvaluation = evaluateAllSchemes(passmark, overallScore)
    val allSchemesEvaluationMap = allSchemesEvaluation.map { x => x.schemeName -> x.result }.toMap

    val location1Scheme1Result = Some(allSchemesEvaluationMap(location1Scheme1))
    val location1Scheme2Result = location1Scheme2 map allSchemesEvaluationMap
    val location2Scheme1Result = location2Scheme1 map allSchemesEvaluationMap
    val location2Scheme2Result = location2Scheme2 map allSchemesEvaluationMap

    val alternativeSchemeResult: Option[Result] = alternativeScheme.collect {
      case true =>
        val allNotChosenSchemes = List(Some(location1Scheme1), location1Scheme2, location2Scheme1, location2Scheme2).flatten
        evaluateAlternativeSchemes(allSchemesEvaluationMap, allNotChosenSchemes)
    }

    AssessmentRuleCategoryResult(None, location1Scheme1Result, location1Scheme2Result, location2Scheme1Result,
      location2Scheme2Result, alternativeSchemeResult, Some(competencyAverage), Some(allSchemesEvaluation))
  }

  def evaluateScore(schemeName: String, passmark: AssessmentCentrePassMarkSettingsResponse, overallScore: Double): Result = {
    val passmarkSetting = passmark.schemes.find(_.schemeName == schemeName)
      .getOrElse(throw new IllegalStateException(s"schemeName=$schemeName is not set in Passmark settings"))
      .overallPassMarks
      .getOrElse(throw new IllegalStateException(s"Scheme threshold for $schemeName is not set in Passmark settings"))

    determineStatus(overallScore, passmarkSetting)
  }

  def evaluateAllSchemes(passmark: AssessmentCentrePassMarkSettingsResponse, overallScore: Double): List[PerSchemeEvaluation] =
    passmark.schemes.map(_.schemeName).map { schemeName =>
      val result = evaluateScore(schemeName, passmark, overallScore)
      PerSchemeEvaluation(schemeName, result)
    }

  def evaluateAlternativeSchemes(allSchemesEvaluation: Map[String, Result], schemesExcluded: List[String]): Result = {
    val allNotChosenSchemes = allSchemesEvaluation.filterNot {
      case (schemeName, _) =>
        schemesExcluded.contains(schemeName)
    }

    val evaluation = allNotChosenSchemes.values.toList
    if (evaluation.contains(Green)) {
      Green
    } else if (evaluation.contains(Amber)) {
      Amber
    } else {
      Red
    }
  }

  private def determineStatus(overallScore: Double, passmarkThreshold: PassMarkSchemeThreshold): Result = {
    if (overallScore <= passmarkThreshold.failThreshold) {
      Red
    } else if (overallScore >= passmarkThreshold.passThreshold) {
      Green
    } else {
      Amber
    }
  }
}
