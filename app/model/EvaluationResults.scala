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
import play.api.libs.json.Json
import reactivemongo.bson.Macros

object EvaluationResults {

  sealed trait Result {
    def toReportReadableString: String

    def +(that: Result): Result
  }

  case object Green extends Result {
    def toReportReadableString: String = PassFail.Pass.toString

    def +(that: Result): Result = that match {
      case Green => this
      case Red => Red
      case Amber => Amber
      case Withdrawn => Withdrawn
    }
  }

  case object Amber extends Result {
    def toReportReadableString: String = "Amber"

    def +(that: Result): Result = that match {
      case Green => Green
      case Red => Red
      case Amber => this
      case Withdrawn => Withdrawn
    }
  }

  case object Red extends Result {
    def toReportReadableString: String = PassFail.Fail.toString

    def +(that: Result): Result = that match {
      case Withdrawn => Withdrawn
      case _ => this
    }
  }

  //Not an evaluation status but no where else really good to put this.
  case object Withdrawn extends Result {
    def toReportReadableString: String = "Withdrawn"

    def +(that: Result): Result = this
  }

  object Result {
    def apply(s: String): Result = s match {
      case "Red" => Red
      case "Green" => Green
      case "Amber" => Amber
      case "Withdrawn" => Withdrawn
    }

    def fromPassFail(s: String): EvaluationResults.Result = PassFail.withName(s) match {
      case PassFail.Pass => EvaluationResults.Green
      case PassFail.Fail => EvaluationResults.Red
    }

  }

  object PassFail extends Enumeration {
    val Pass, Fail = Value
  }

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

  object CompetencyAverageResult {
    implicit val competencyAverageResultFormat = Json.format[CompetencyAverageResult]
    implicit val competencyAverageResultHandler = Macros.handler[CompetencyAverageResult]
  }

  case class AssessmentEvaluationResult(
    passedMinimumCompetencyLevel: Option[Boolean],
    competencyAverageResult: CompetencyAverageResult,
    schemesEvaluation: Seq[SchemeEvaluationResult])
}

