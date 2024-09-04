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

package services.evaluation

import model.EvaluationResults.ExerciseAverageResult
import model.UniqueIdentifier
import model.assessmentscores.{AssessmentScoresAllExercises, AssessmentScoresExercise}
import testkit.UnitSpec

class AssessmentScoreCalculatorSpec extends UnitSpec {

  object AssessmentScoreCalculatorUnderTest extends AssessmentScoreCalculator

  val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  "Assessment score calculator" should {
    "correctly calculate the overall score from the individual exercise averages" in {
      val applicationId = UniqueIdentifier.randomUniqueIdentifier
      val assessmentScores = AssessmentScoresAllExercises(
        applicationId,
        exercise1 = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          overallAverage = Some(3.125)
        )),
        exercise2 = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          overallAverage = Some(2.0)
        )),
        exercise3 = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          overallAverage = Some(2.5)
        ))
      )

      val result = AssessmentScoreCalculatorUnderTest.fetchExerciseAverages(assessmentScores, applicationId.toString)

      val expected = ExerciseAverageResult(
        exercise1Average = 3.125,
        exercise2Average = 2.0,
        exercise3Average = 2.5,
        overallScore = 7.625
      )

      result mustBe expected
    }
  }
}
