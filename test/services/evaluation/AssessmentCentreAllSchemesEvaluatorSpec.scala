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

import model.EvaluationResults.{ Amber, CompetencyAverageResult, Green, Red }
import model.SchemeId
import model.exchange.passmarksettings._
import model.persisted.SchemeEvaluationResult
import org.joda.time.DateTime
import testkit.UnitSpec

class AssessmentCentreAllSchemesEvaluatorSpec extends UnitSpec {

  object EvaluatorUnderTest extends AssessmentCentreAllSchemesEvaluator

  val appId = "appId"
  val commercial = SchemeId("Commercial")

  def competencyAverageResult(c1Avg: Double, c2Avg: Double, c3Avg: Double, c4Avg: Double, overallAvg: Double) =
    CompetencyAverageResult(
      seeingTheBigPictureAverage = c1Avg,
      makingEffectiveDecisionsAverage = c2Avg,
      communicatingAndInfluencingAverage = c3Avg,
      workingTogetherDevelopingSelfAndOthersAverage = c4Avg,
      overallScore = overallAvg
    )

  "Assessment evaluator calculator" should {
    "evaluate to Green when all pass marks are zero and all scores are zero" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(0d, 0d),
            makingEffectiveDecisions = PassMarkThreshold(0d, 0d),
            communicatingAndInfluencing = PassMarkThreshold(0d, 0d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(0d, 0d, 0d, 0d, 0d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Green.toString))
    }

    "evaluate to Green when no pass marks are zero and all scores hit the pass mark" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(0d, 1d),
            makingEffectiveDecisions = PassMarkThreshold(0d, 1d),
            communicatingAndInfluencing = PassMarkThreshold(0d, 1d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0d, 1d),
            overall = PassMarkThreshold(0d, 1d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(1d, 1d, 1d, 1d, 1d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Green.toString))
    }

    "evaluate to Red when seeingTheBigPicture is Red and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(2d, 2d),
            makingEffectiveDecisions = PassMarkThreshold(0d, 0d),
            communicatingAndInfluencing = PassMarkThreshold(0d, 0d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(1d, 0d, 0d, 0d, 0d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Red.toString))
    }

    "evaluate to Red when makingEffectiveDecisions is Red and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(0d, 0d),
            makingEffectiveDecisions = PassMarkThreshold(2d, 2d),
            communicatingAndInfluencing = PassMarkThreshold(0d, 0d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(0d, 1d, 0d, 0d, 0d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Red.toString))
    }

    "evaluate to Red when communicatingAndInfluencing is Red and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(0d, 0d),
            makingEffectiveDecisions = PassMarkThreshold(0d, 0d),
            communicatingAndInfluencing = PassMarkThreshold(2d, 2d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(0d, 0d, 1d, 0d, 0d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Red.toString))
    }

    "evaluate to Red when workingTogetherDevelopingSelfAndOthers is Red and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(0d, 0d),
            makingEffectiveDecisions = PassMarkThreshold(0d, 0d),
            communicatingAndInfluencing = PassMarkThreshold(0d, 0d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(2d, 2d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(0d, 0d, 0d, 1d, 0d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Red.toString))
    }

    "evaluate to Red when overall is Red and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(0d, 0d),
            makingEffectiveDecisions = PassMarkThreshold(0d, 0d),
            communicatingAndInfluencing = PassMarkThreshold(0d, 0d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(2d, 2d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(0d, 0d, 0d, 0d, 1d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Red.toString))
    }

    "evaluate to Amber when seeingTheBigPicture is Amber and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(1d, 2d),
            makingEffectiveDecisions = PassMarkThreshold(0d, 0d),
            communicatingAndInfluencing = PassMarkThreshold(0d, 0d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(1d, 0d, 0d, 0d, 0d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Amber.toString))
    }

    "evaluate to Amber when makingEffectiveDecisions is Amber and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(0d, 0d),
            makingEffectiveDecisions = PassMarkThreshold(1d, 2d),
            communicatingAndInfluencing = PassMarkThreshold(0d, 0d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(0d, 1d, 0d, 0d, 0d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Amber.toString))
    }

    "evaluate to Amber when communicatingAndInfluencing is Amber and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(0d, 0d),
            makingEffectiveDecisions = PassMarkThreshold(0d, 0d),
            communicatingAndInfluencing = PassMarkThreshold(1d, 2d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(0d, 0d, 1d, 0d, 0d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Amber.toString))
    }

    "evaluate to Amber when workingTogetherDevelopingSelfAndOthers is Amber and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(0d, 0d),
            makingEffectiveDecisions = PassMarkThreshold(0d, 0d),
            communicatingAndInfluencing = PassMarkThreshold(0d, 0d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(1d, 2d),
            overall = PassMarkThreshold(0d, 0d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(0d, 0d, 0d, 1d, 0d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Amber.toString))
    }

    "evaluate to Amber when overall is Amber and the others are Green" in {
      val passMarks = AssessmentCentrePassMarkSettings(
        schemes = List(
          AssessmentCentrePassMark(commercial, AssessmentCentrePassMarkThresholds(
            seeingTheBigPicture = PassMarkThreshold(0d, 0d),
            makingEffectiveDecisions = PassMarkThreshold(0d, 0d),
            communicatingAndInfluencing = PassMarkThreshold(0d, 0d),
            workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0d, 0d),
            overall = PassMarkThreshold(1d, 2d)
          ))
        ), version = "v1", createDate = DateTime.now(), createdBy = "test"
      )
      val averageScores = competencyAverageResult(0d, 0d, 0d, 0d, 1d)
      val schemes = Seq(commercial)

      val result = EvaluatorUnderTest.evaluateSchemes(appId, passMarks, averageScores, schemes)
      result mustBe Seq(SchemeEvaluationResult(commercial, Amber.toString))
    }
  }
}
