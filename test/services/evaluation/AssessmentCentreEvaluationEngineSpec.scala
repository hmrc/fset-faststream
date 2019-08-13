/*
 * Copyright 2019 HM Revenue & Customs
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

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.EvaluationResults.{ Amber, CompetencyAverageResult, Green, Red }
import model.exchange.passmarksettings._
import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresExercise }
import model.persisted.SchemeEvaluationResult
import model.{ AssessmentPassMarksSchemesAndScores, SchemeId, UniqueIdentifier }
import org.joda.time.DateTime
import services.BaseServiceSpec

class AssessmentCentreEvaluationEngineSpec extends BaseServiceSpec {

  val commercial = "Commercial"
  val digitalAndTechnology = "DigitalAndTechnology"
  val diplomaticService = "DiplomaticService"

  // The pass marks which the evaluation engine uses to work out if each scheme has passed/failed
  val passMarkSettings = AssessmentCentrePassMarkSettings(List(
    AssessmentCentrePassMark(SchemeId(commercial), AssessmentCentrePassMarkThresholds(PassMarkThreshold(10.0, 15.0))),
    AssessmentCentrePassMark(SchemeId(digitalAndTechnology), AssessmentCentrePassMarkThresholds(PassMarkThreshold(10.0, 16.0))),
    AssessmentCentrePassMark(SchemeId(diplomaticService), AssessmentCentrePassMarkThresholds(PassMarkThreshold(16.0, 20.0)))),
    "1", DateTime.now(), "user")

  val applicationId = UniqueIdentifier.randomUniqueIdentifier
  val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  // The scores awarded to the candidate by assessor/reviewer
  val candidateScores = AssessmentScoresAllExercises(applicationId,
    analysisExercise = Some(
      AssessmentScoresExercise(
        attended = true,
        makingEffectiveDecisionsAverage = Some(5.0),
        communicatingAndInfluencingAverage = Some(4.0),
        seeingTheBigPictureAverage = Some(4.0),
        updatedBy = updatedBy
      )),
    groupExercise = Some(
      AssessmentScoresExercise(
        attended = true,
        makingEffectiveDecisionsAverage = Some(5.0),
        workingTogetherDevelopingSelfAndOthersAverage = Some(2.0),
        communicatingAndInfluencingAverage = Some(4.0),
        updatedBy = updatedBy
      )),
    leadershipExercise = Some(
      AssessmentScoresExercise(
        attended = true,
        workingTogetherDevelopingSelfAndOthersAverage = Some(4.0),
        communicatingAndInfluencingAverage = Some(4.0),
        seeingTheBigPictureAverage = Some(4.0),
        updatedBy = updatedBy
      ))
  )

  // List of schemes for which the candidate will be evaluated
  val candidateSchemes = List(
    SchemeId(commercial),
    SchemeId(digitalAndTechnology),
    SchemeId(diplomaticService)
  )

  val evaluationEngine = AssessmentCentreEvaluationEngine

  "Assessment Centre Passmark Rules engine evaluation" should {
    "evaluate to passedMinimumCompetencyLevel=false when minimum competency level is enabled and not met" in {
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = true, minimumCompetencyLevelScore = Some(3.1))
      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, candidateSchemes, candidateScores)

      val result = evaluationEngine.evaluate(candidateScore, config)
      result.passedMinimumCompetencyLevel mustBe Some(false)
      result.schemesEvaluation mustBe List(
        SchemeEvaluationResult(SchemeId(commercial), Red.toString),
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), Red.toString),
        SchemeEvaluationResult(SchemeId(diplomaticService), Red.toString)
      )

      val expectedCompetencyAverage = CompetencyAverageResult(
        analysisAndDecisionMakingAverage = 5.0,
        buildingProductiveRelationshipsAverage = 3.0,
        leadingAndCommunicatingAverage = 4.0,
        strategicApproachToObjectivesAverage = 4.0,
        overallScore = 16.0
      )
      result.competencyAverageResult mustBe expectedCompetencyAverage
    }

    "evaluate to passedMinimumCompetencyLevel is empty and evaluate the schemes when MCL is turned off" in {
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = false, minimumCompetencyLevelScore = Some(3.0))
      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, candidateSchemes, candidateScores)

      val result = evaluationEngine.evaluate(candidateScore, config)
      result.passedMinimumCompetencyLevel mustBe None
      result.schemesEvaluation mustBe List(
        SchemeEvaluationResult(SchemeId(commercial), Green.toString),
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), Green.toString),
        SchemeEvaluationResult(SchemeId(diplomaticService), Amber.toString)
      )
    }

    "evaluate to passedMinimumCompetencyLevel=true and evaluate the schemes" in {
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = true, Some(3.0))
      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, candidateSchemes, candidateScores)

      val result = evaluationEngine.evaluate(candidateScore, config)
      result.passedMinimumCompetencyLevel mustBe Some(true)
      result.schemesEvaluation mustBe List(
        SchemeEvaluationResult(SchemeId(commercial), Green.toString),
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), Green.toString),
        SchemeEvaluationResult(SchemeId(diplomaticService), Amber.toString)
      )
    }

    "evaluate to amber or red" in {
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = true, Some(2.75))
      val candidateScores = AssessmentScoresAllExercises(applicationId,
        analysisExercise = Some(
          AssessmentScoresExercise(makingEffectiveDecisionsAverage = Some(2.5), communicatingAndInfluencingAverage = Some(3.5),
            seeingTheBigPictureAverage = Some(4.0), updatedBy = updatedBy, attended = true
          )),
        groupExercise = Some(
          AssessmentScoresExercise(makingEffectiveDecisionsAverage = Some(3.0), workingTogetherDevelopingSelfAndOthersAverage = Some(4.5),
            communicatingAndInfluencingAverage = Some(3.5), updatedBy = updatedBy, attended = true
          )),
        leadershipExercise = Some(
          AssessmentScoresExercise(workingTogetherDevelopingSelfAndOthersAverage = Some(3.0), communicatingAndInfluencingAverage = Some(3.5),
            seeingTheBigPictureAverage = Some(4.0), updatedBy = updatedBy, attended = true
          ))
      )

      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, candidateSchemes, candidateScores)

      val result = evaluationEngine.evaluate(candidateScore, config)
      result.passedMinimumCompetencyLevel mustBe Some(true)
      result.schemesEvaluation mustBe List(
        SchemeEvaluationResult(SchemeId(commercial), Amber.toString),
        SchemeEvaluationResult(SchemeId(digitalAndTechnology), Amber.toString),
        SchemeEvaluationResult(SchemeId(diplomaticService), Red.toString)
      )

      val expectedCompetencyAverage = CompetencyAverageResult(
        analysisAndDecisionMakingAverage = 2.75,
        buildingProductiveRelationshipsAverage = 3.75,
        leadingAndCommunicatingAverage = 3.5,
        strategicApproachToObjectivesAverage = 4.0,
        overallScore = 14.0
      )
      result.competencyAverageResult mustBe expectedCompetencyAverage
    }
  }
}
