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

package model

import model.persisted.SchemeEvaluationResult

object EvaluationResults {
  sealed trait Result {
    def toReportReadableString: String
  }
  case object Green extends Result {
    def toReportReadableString: String = "Pass"
  }
  case object Amber extends Result {
    def toReportReadableString: String = "Amber"
  }
  case object Red extends Result {
    def toReportReadableString: String = "Fail"
  }

  object Result {
    def apply(s: String): Result = s match {
      case "Red" => Red
      case "Green" => Green
      case "Amber" => Amber
    }
  }

  @deprecated("This should be deleted", since = "31/07/2017")
  case class RuleCategoryResult(location1Scheme1: Result, location1Scheme2: Option[Result],
    location2Scheme1: Option[Result], location2Scheme2: Option[Result], alternativeScheme: Option[Result])

  case class CompetencyAverageResult(
    analysisAndDecisionMakingAverage: Double,
    buildingProductiveRelationshipsAverage: Double,
    leadingAndCommunicatingAverage: Double,
    strategicApproachToObjectivesAverage: Double,
    overallScore: Double) {

    def competencyAverageScores = List(
      analysisAndDecisionMakingAverage, buildingProductiveRelationshipsAverage,
      leadingAndCommunicatingAverage, strategicApproachToObjectivesAverage
    )
  }

  @deprecated("Use SchemeEvaluationResult with SchemeId", since = "10/10/2016")
  case class PerSchemeEvaluation(schemeName: String, result: Result)

  case class AssessmentEvaluationResult(
    passedMinimumCompetencyLevel: Option[Boolean],
    competencyAverageResult: CompetencyAverageResult,
    schemesEvaluation: List[SchemeEvaluationResult])
}
