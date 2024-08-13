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

import model.EvaluationResults.{Amber, ExerciseAverageResult, Green, Red}
import model.Schemes
import model.exchange.passmarksettings._
import model.persisted.SchemeEvaluationResult
import testkit.UnitSpec

import java.time.OffsetDateTime

class AssessmentCentreAllSchemesEvaluatorSpec extends UnitSpec with Schemes {

  object EvaluatorUnderTest extends AssessmentCentreAllSchemesEvaluator

  val appId = "appId"

  def exerciseAverageResult(e1Avg: Double, e2Avg: Double, e3Avg: Double, overallAvg: Double) =
    ExerciseAverageResult(
      exercise1Average = e1Avg,
      exercise2Average = e2Avg,
      exercise3Average = e3Avg,
      overallScore = overallAvg
    )

  "Assessment evaluator calculator" should {
    "evaluate to Green when all pass marks are zero and all scores are zero" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(0d, 0d),
            exercise2 = PassMarkThreshold(0d, 0d),
            exercise3 = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(0d, 0d, 0d, 0d)
      val schemes = Seq(Commercial)
      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Green.toString))
    }

    "evaluate to Green when no pass marks are zero and all scores hit the pass mark" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(0d, 1d),
            exercise2 = PassMarkThreshold(0d, 1d),
            exercise3 = PassMarkThreshold(0d, 1d),
            overall = PassMarkThreshold(0d, 1d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(1d, 1d, 1d, 1d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Green.toString))
    }

    "evaluate to Red when seeingTheBigPicture is Red and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(2d, 2d),
            exercise2 = PassMarkThreshold(0d, 0d),
            exercise3 = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(1d, 0d, 0d, 0d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Red.toString))
    }

    "evaluate to Red when makingEffectiveDecisions is Red and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(0d, 0d),
            exercise2 = PassMarkThreshold(2d, 2d),
            exercise3 = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(0d, 1d, 0d, 0d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Red.toString))
    }

    "evaluate to Red when communicatingAndInfluencing is Red and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(0d, 0d),
            exercise2 = PassMarkThreshold(0d, 0d),
            exercise3 = PassMarkThreshold(2d, 2d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(0d, 0d, 1d, 0d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Red.toString))
    }

    "evaluate to Red when workingTogetherDevelopingSelfAndOthers is Red and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(0d, 0d),
            exercise2 = PassMarkThreshold(0d, 0d),
            exercise3 = PassMarkThreshold(2d, 2d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(0d, 0d, 0d, 1d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Red.toString))
    }

    "evaluate to Red when overall is Red and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(0d, 0d),
            exercise2 = PassMarkThreshold(0d, 0d),
            exercise3 = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(2d, 2d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(0d, 0d, 0d, 1d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Red.toString))
    }

    "evaluate to Amber when seeingTheBigPicture is Amber and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(1d, 2d),
            exercise2 = PassMarkThreshold(0d, 0d),
            exercise3 = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(1d, 0d, 0d, 0d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Amber.toString))
    }

    "evaluate to Amber when makingEffectiveDecisions is Amber and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(0d, 0d),
            exercise2 = PassMarkThreshold(1d, 2d),
            exercise3 = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(0d, 1d, 0d, 0d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Amber.toString))
    }

    "evaluate to Amber when communicatingAndInfluencing is Amber and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(0d, 0d),
            exercise2 = PassMarkThreshold(0d, 0d),
            exercise3 = PassMarkThreshold(1d, 2d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(0d, 0d, 1d, 0d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Amber.toString))
    }

    "evaluate to Amber when workingTogetherDevelopingSelfAndOthers is Amber and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(0d, 0d),
            exercise2 = PassMarkThreshold(0d, 0d),
            exercise3 = PassMarkThreshold(1d, 2d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(0d, 0d, 1d, 0d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Amber.toString))
    }

    "evaluate to Amber when overall is Amber and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettingsPersistence(
        schemes = List(
          AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
            exercise1 = PassMarkThreshold(0d, 0d),
            exercise2 = PassMarkThreshold(0d, 0d),
            exercise3 = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(1d, 2d)
          ))
        ), version = "v1", createDate = OffsetDateTime.now, createdBy = "test"
      )
      val averageScores = exerciseAverageResult(0d, 0d, 0d, 1d)
      val schemes = Seq(Commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(Commercial, Amber.toString))
    }
  }
}
