/*
 * Copyright 2024 HM Revenue & Customs
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

package services.assessmentcentre

import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{CompetencyAverageResult, ExerciseAverageResult}
import model.persisted.SchemeEvaluationResult
import model.{ProgressStatuses, SchemeId}

case class AssessmentScoreEvaluationTestExpectation(
                                                     applicationStatus: Option[ApplicationStatus],
                                                     progressStatus: Option[ProgressStatuses.ProgressStatus],
                                                     passmarkVersion: Option[String],
                                                     exercise1Average: Option[Double],
                                                     exercise2Average: Option[Double],
                                                     exercise3Average: Option[Double],
                                                     exerciseOverallScore: Option[Double],
                                                     schemesEvaluation: Option[String]
) {

  override def toString =
    s"applicationStatus=$applicationStatus," +
      s"progressStatus=$progressStatus," +
      s"passmarkVersion=$passmarkVersion," +
      s"exercise1Average=$exercise1Average," +
      s"exercise2Average=$exercise2Average," +
      s"exercise3Average=$exercise3Average," +
      s"exerciseOverallScore=$exerciseOverallScore," +
      s"schemesEvaluation=$schemesEvaluation"

  def exerciseAverage: Option[ExerciseAverageResult] = {
    val allResults = List(
      exercise1Average, exercise2Average, exercise3Average, exerciseOverallScore
    )

    val data = s"writtenExerciseAverage=$exercise1Average, " +
      s"stakeholderCommunicationExerciseAverage=$exercise2Average, " +
      s"personalDevelopmentConversationAverage=$exercise3Average, " +
      s"exerciseOverallScore=$exerciseOverallScore"

    require(allResults.forall(_.isDefined) || allResults.forall(_.isEmpty), s"All exercise averages or none of them must be defined - $data")

    if (allResults.forall(_.isDefined)) {
      Some(ExerciseAverageResult(
        exercise1Average.get,
        exercise2Average.get,
        exercise3Average.get,
        exerciseOverallScore.get))
    } else {
      None
    }
  }

  def allSchemesEvaluationExpectations: Option[List[SchemeEvaluationResult]] =
    schemesEvaluation.map { s =>
      s.split("\\|").map { schemeAndResult =>
        val Array(scheme, result) = schemeAndResult.split(":")
        SchemeEvaluationResult(SchemeId(scheme), result)
      }.toList
    }
}
