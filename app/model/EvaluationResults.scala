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

  case class ExerciseAverageResult(
                                    exercise1Average: Double,
                                    exercise2Average: Double,
                                    exercise3Average: Double,
                                    overallScore: Double) {

    override def toString: String = s"exercise1Average=$exercise1Average," +
      s"exercise2Average=$exercise2Average," +
      s"exercise3Average=$exercise3Average," +
      s"overallScore=$overallScore"
  }

  object ExerciseAverageResult {
    implicit val exerciseAverageResultFormat: OFormat[ExerciseAverageResult] = Json.format[ExerciseAverageResult]
  }

  case class FsacResults(exerciseAverageResult: ExerciseAverageResult) {

    override def toString: String =
      s"exercise1Average=${exerciseAverageResult.exercise1Average}," +
      s"exercise2Average=${exerciseAverageResult.exercise2Average}," +
      s"exercise3Average=${exerciseAverageResult.exercise3Average}," +
      s"overallScore=${exerciseAverageResult.overallScore}"
  }

  case class AssessmentEvaluationResult(
                                         fsacResults: FsacResults,
                                         schemesEvaluation: Seq[SchemeEvaluationResult]
                                       )
}
