/*
 * Copyright 2020 HM Revenue & Customs
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
    // Calculated as the average of makingEffectiveDecisionsAverage field in analysis exercise and group exercise
    "correctly calculate makingEffectiveDecisions" in {

      val assessmentScores = AssessmentScoresAllExercises(
        applicationId = UniqueIdentifier.randomUniqueIdentifier,
        analysisExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          makingEffectiveDecisionsAverage = Some(3.231)
        )),
        groupExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          makingEffectiveDecisionsAverage = Some(2.345)
        )),
        leadershipExercise = None
      )

      val result = AssessmentScoreCalculatorUnderTest.countAverage(assessmentScores)

      val expected = CompetencyAverageResult(
        makingEffectiveDecisionsAverage = 2.788,
        workingTogetherDevelopingSelfAndOthersAverage = 0,
        communicatingAndInfluencingAverage = 0,
        seeingTheBigPictureAverage = 0,
        overallScore = 2.79)

      result mustBe expected
    }

    // Calculated as the average of workingTogetherDevelopingSelfAndOthers field in group exercise and leadership exercise
    "correctly calculate workingTogetherDevelopingSelfAndOthersAverage" in {

      val assessmentScores = AssessmentScoresAllExercises(
        applicationId = UniqueIdentifier.randomUniqueIdentifier,
        analysisExercise = None,
        groupExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          workingTogetherDevelopingSelfAndOthersAverage = Some(2.345)
        )),
        leadershipExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          workingTogetherDevelopingSelfAndOthersAverage = Some(3.231)
        ))
      )

      val result = AssessmentScoreCalculatorUnderTest.countAverage(assessmentScores)

      val expected = CompetencyAverageResult(
        makingEffectiveDecisionsAverage = 0,
        workingTogetherDevelopingSelfAndOthersAverage = 2.788,
        communicatingAndInfluencingAverage = 0,
        seeingTheBigPictureAverage = 0,
        overallScore = 2.79)

      result mustBe expected
    }

    // Calculated as the average of communicatingAndInfluencingAverage field in analysis exercise, group exercise and leadership exercise
    "correctly calculate communicatingAndInfluencingAverage" in {

      val assessmentScores = AssessmentScoresAllExercises(
        applicationId = UniqueIdentifier.randomUniqueIdentifier,
        analysisExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          communicatingAndInfluencingAverage = Some(2.345)
        )),
        groupExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          communicatingAndInfluencingAverage = Some(2.345)
        )),
        leadershipExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          communicatingAndInfluencingAverage = Some(2.345)
        ))
      )

      val result = AssessmentScoreCalculatorUnderTest.countAverage(assessmentScores)

      val expected = CompetencyAverageResult(
        makingEffectiveDecisionsAverage = 0,
        workingTogetherDevelopingSelfAndOthersAverage = 0,
        communicatingAndInfluencingAverage = 2.345,
        seeingTheBigPictureAverage = 0,
        overallScore = 2.35)

      result mustBe expected
    }

    // Calculated as the average of seeingTheBigPictureAverage field in analysis exercise and leadership exercise
    "correctly calculate seeingTheBigPictureAverage" in {

      val assessmentScores = AssessmentScoresAllExercises(
        applicationId = UniqueIdentifier.randomUniqueIdentifier,
        analysisExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          seeingTheBigPictureAverage = Some(2.345)
        )),
        leadershipExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          seeingTheBigPictureAverage = Some(3.231)
        ))
      )

      val result = AssessmentScoreCalculatorUnderTest.countAverage(assessmentScores)

      val expected = CompetencyAverageResult(
        makingEffectiveDecisionsAverage = 0,
        workingTogetherDevelopingSelfAndOthersAverage = 0,
        communicatingAndInfluencingAverage = 0,
        seeingTheBigPictureAverage = 2.788,
        overallScore = 2.79)

      result mustBe expected
    }

    "handle all averages" in {
      val assessmentScores = AssessmentScoresAllExercises(
        applicationId = UniqueIdentifier.randomUniqueIdentifier,
        analysisExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          seeingTheBigPictureAverage = Some(5.544),
          makingEffectiveDecisionsAverage = Some(5.544),
          communicatingAndInfluencingAverage = Some(5.544),
          workingTogetherDevelopingSelfAndOthersAverage = Some(5.544)
        )),
        groupExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          seeingTheBigPictureAverage = Some(5.544),
          makingEffectiveDecisionsAverage = Some(5.544),
          communicatingAndInfluencingAverage = Some(5.544),
          workingTogetherDevelopingSelfAndOthersAverage = Some(5.544)
        )),
        leadershipExercise = Some(AssessmentScoresExercise(
          attended = true,
          updatedBy = updatedBy,
          seeingTheBigPictureAverage = Some(5.544),
          makingEffectiveDecisionsAverage = Some(5.544),
          communicatingAndInfluencingAverage = Some(5.544),
          workingTogetherDevelopingSelfAndOthersAverage = Some(5.544)
        ))
      )

      val result = AssessmentScoreCalculatorUnderTest.countAverage(assessmentScores)

      val expected = CompetencyAverageResult(
        makingEffectiveDecisionsAverage = 5.544,
        workingTogetherDevelopingSelfAndOthersAverage = 5.544,
        communicatingAndInfluencingAverage = 5.544,
        seeingTheBigPictureAverage = 5.544,
        overallScore = 22.16)

      result mustBe expected
    }
  }
}
