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

import model.EvaluationResults.CompetencyAverageResult
import model.UniqueIdentifier
import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresExercise }
import testkit.UnitSpec

class AssessmentScoreCalculatorSpec extends UnitSpec {

  object AssessmentScoreCalculatorUnderTest extends AssessmentScoreCalculator

  val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  "Assessment score calculator" should {
    // Calculated as the average of analysisAndDecisionMakingAverage field in analysis exercise and group exercise
    "correctly calculate analysis and decision making average" in {

      val assessmentScores = AssessmentScoresAllExercises(
        applicationId = UniqueIdentifier.randomUniqueIdentifier,
        analysisExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          analysisAndDecisionMakingAverage = Some(3.231)
        )),
        groupExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          analysisAndDecisionMakingAverage = Some(2.345)
        )),
        leadershipExercise = None
      )

      val result = AssessmentScoreCalculatorUnderTest.countAverage(assessmentScores)

      val expected = CompetencyAverageResult(
        analysisAndDecisionMakingAverage = 2.788,
        buildingProductiveRelationshipsAverage = 0,
        leadingAndCommunicatingAverage = 0,
        strategicApproachToObjectivesAverage = 0,
        overallScore = 2.79)

      result mustBe expected
    }

    // Calculated as the average of buildingProductiveRelationshipsAverage field in group exercise and leadership exercise
    "correctly calculate building relationships average" in {

      val assessmentScores = AssessmentScoresAllExercises(
        applicationId = UniqueIdentifier.randomUniqueIdentifier,
        analysisExercise = None,
        groupExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          buildingProductiveRelationshipsAverage = Some(2.345)
        )),
        leadershipExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          buildingProductiveRelationshipsAverage = Some(3.231)
        ))
      )

      val result = AssessmentScoreCalculatorUnderTest.countAverage(assessmentScores)

      val expected = CompetencyAverageResult(
        analysisAndDecisionMakingAverage = 0,
        buildingProductiveRelationshipsAverage = 2.788,
        leadingAndCommunicatingAverage = 0,
        strategicApproachToObjectivesAverage = 0,
        overallScore = 2.79)

      result mustBe expected
    }

    // Calculated as the average of leadingAndCommunicatingAverage field in analysis exercise, group exercise and leadership exercise
    "correctly calculate leading and communicating average" in {

      val assessmentScores = AssessmentScoresAllExercises(
        applicationId = UniqueIdentifier.randomUniqueIdentifier,
        analysisExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          buildingProductiveRelationshipsAverage = Some(2.345)
        )),
        groupExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          buildingProductiveRelationshipsAverage = Some(2.366)
        )),
        leadershipExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          buildingProductiveRelationshipsAverage = Some(3.231)
        ))
      )

      val result = AssessmentScoreCalculatorUnderTest.countAverage(assessmentScores)

      val expected = CompetencyAverageResult(
        analysisAndDecisionMakingAverage = 0,
        buildingProductiveRelationshipsAverage = 2.7985,
        leadingAndCommunicatingAverage = 0,
        strategicApproachToObjectivesAverage = 0,
        overallScore = 2.8)

      result mustBe expected
    }

    // Calculated as the average of strategicApproachToObjectivesAverage field in analysis exercise and leadership exercise
    "correctly calculate strategic approach to objectives average" in {

      val assessmentScores = AssessmentScoresAllExercises(
        applicationId = UniqueIdentifier.randomUniqueIdentifier,
        analysisExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          strategicApproachToObjectivesAverage = Some(2.345)
        )),
        leadershipExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          strategicApproachToObjectivesAverage = Some(3.231)
        ))
      )

      val result = AssessmentScoreCalculatorUnderTest.countAverage(assessmentScores)

      val expected = CompetencyAverageResult(
        analysisAndDecisionMakingAverage = 0,
        buildingProductiveRelationshipsAverage = 0,
        leadingAndCommunicatingAverage = 0,
        strategicApproachToObjectivesAverage = 2.788,
        overallScore = 2.79)

      result mustBe expected
    }

    "handle all averages" in {
      val assessmentScores = AssessmentScoresAllExercises(
        applicationId = UniqueIdentifier.randomUniqueIdentifier,
        analysisExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          strategicApproachToObjectivesAverage = Some(5.544),
          analysisAndDecisionMakingAverage = Some(5.544),
          leadingAndCommunicatingAverage = Some(5.544),
          buildingProductiveRelationshipsAverage = Some(5.544)
        )),
        groupExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          strategicApproachToObjectivesAverage = Some(5.544),
          analysisAndDecisionMakingAverage = Some(5.544),
          leadingAndCommunicatingAverage = Some(5.544),
          buildingProductiveRelationshipsAverage = Some(5.544)
        )),
        leadershipExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          strategicApproachToObjectivesAverage = Some(5.544),
          analysisAndDecisionMakingAverage = Some(5.544),
          leadingAndCommunicatingAverage = Some(5.544),
          buildingProductiveRelationshipsAverage = Some(5.544)
        ))
      )

      val result = AssessmentScoreCalculatorUnderTest.countAverage(assessmentScores)

      val expected = CompetencyAverageResult(
        analysisAndDecisionMakingAverage = 5.544,
        buildingProductiveRelationshipsAverage = 5.544,
        leadingAndCommunicatingAverage = 5.544,
        strategicApproachToObjectivesAverage = 5.544,
        overallScore = 22.16)

      result mustBe expected
    }
  }
}
