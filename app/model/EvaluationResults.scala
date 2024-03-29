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

package model

import model.persisted.SchemeEvaluationResult
import play.api.libs.json.{Json, OFormat}

object EvaluationResults {

  //scalastyle:off method.name
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
  //scalastyle:off method.name

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
                                      makingEffectiveDecisionsAverage: Double,
                                      workingTogetherDevelopingSelfAndOthersAverage: Double,
                                      communicatingAndInfluencingAverage: Double,
                                      seeingTheBigPictureAverage: Double,
                                      overallScore: Double
                                    ) {

    override def toString: String = s"makingEffectiveDecisionsAverage=$makingEffectiveDecisionsAverage," +
      s"workingTogetherDevelopingSelfAndOthersAverage=$workingTogetherDevelopingSelfAndOthersAverage," +
      s"communicatingAndInfluencingAverage=$communicatingAndInfluencingAverage," +
      s"seeingTheBigPictureAverage=$seeingTheBigPictureAverage," +
      s"overallScore=$overallScore"
  }

  object CompetencyAverageResult {
    implicit val competencyAverageResultFormat: OFormat[CompetencyAverageResult] = Json.format[CompetencyAverageResult]
  }

  case class ExerciseAverageResult(
                                    writtenExerciseAverage: Double,
                                    teamExerciseAverage: Double,
                                    leadershipExerciseAverage: Double,
                                    overallScore: Double) {

    override def toString: String = s"writtenExerciseAverage=$writtenExerciseAverage," +
      s"teamExerciseAverage=$teamExerciseAverage," +
      s"leadershipExerciseAverage=$leadershipExerciseAverage," +
      s"overallScore=$overallScore"
  }

  object ExerciseAverageResult {
    implicit val exerciseAverageResultFormat: OFormat[ExerciseAverageResult] = Json.format[ExerciseAverageResult]
  }

  case class FsacResults(competencyAverageResult: CompetencyAverageResult, exerciseAverageResult: ExerciseAverageResult) {

    override def toString: String =
//      s"makingEffectiveDecisionsAverage=${competencyAverageResult.makingEffectiveDecisionsAverage}," +
//      s"workingTogetherDevelopingSelfAndOthersAverage=${competencyAverageResult.workingTogetherDevelopingSelfAndOthersAverage}," +
//      s"communicatingAndInfluencingAverage=${competencyAverageResult.communicatingAndInfluencingAverage}," +
//      s"seeingTheBigPictureAverage=${competencyAverageResult.seeingTheBigPictureAverage}," +
//      s"overallScore=${competencyAverageResult.overallScore}," +
      s"writtenExerciseAverage=${exerciseAverageResult.writtenExerciseAverage}," +
      s"teamExerciseAverage=${exerciseAverageResult.teamExerciseAverage}," +
      s"leadershipExerciseAverage=${exerciseAverageResult.leadershipExerciseAverage}," +
      s"overallScore=${exerciseAverageResult.overallScore}"
  }

  case class AssessmentEvaluationResult(
                                         fsacResults: FsacResults,
                                         schemesEvaluation: Seq[SchemeEvaluationResult]
                                       )
}
