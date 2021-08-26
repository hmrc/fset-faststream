/*
 * Copyright 2021 HM Revenue & Customs
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
import model.exchange.passmarksettings._
import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresExercise }
import model.persisted.SchemeEvaluationResult
import model.{ AssessmentPassMarksSchemesAndScores, SchemeId, UniqueIdentifier }
import org.joda.time.DateTime
import services.BaseServiceSpec

class AssessmentCentreEvaluationEngineSpec extends BaseServiceSpec {

  val commercial = "Commercial"
  val digitalDataTechnologyAndCyber = "DigitalDataTechnologyAndCyber"
  val diplomaticService = "DiplomaticService"

  val applicationId = UniqueIdentifier.randomUniqueIdentifier
  val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  val evaluationEngine = new AssessmentCentreEvaluationEngineImpl

  "Assessment Centre Passmark Rules engine evaluation" should {

    // scheme | c1 | c2 | c3 | c4 | overall | expected result
    // s1     | R  | G  | G  | G  | G       | R
    "evaluate to Red with a single Red and the others Green" in {
      val schemes = List(SchemeId(commercial))

      val passMarkSettings = AssessmentCentrePassMarkSettings(List(
        AssessmentCentrePassMark(SchemeId(commercial), AssessmentCentrePassMarkThresholds(
          seeingTheBigPicture = PassMarkThreshold(1.0, 3.0),
          makingEffectiveDecisions = PassMarkThreshold(1.0, 3.0),
          communicatingAndInfluencing = PassMarkThreshold(1.0, 3.0),
          workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(8.0, 10.2)))),
        version = "v1", createDate = DateTime.now(), "user")

      val candidateScores = AssessmentScoresAllExercises(applicationId,
        writtenExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(0.99),
            makingEffectiveDecisionsAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(3.1),
            updatedBy = updatedBy
          )),
        teamExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            makingEffectiveDecisionsAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(3.1),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          )),
        leadershipExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(0.99),
            communicatingAndInfluencingAverage = Some(3.1),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          ))
      )

      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, schemes, candidateScores)
      val result = evaluationEngine.evaluate(candidateScore)
      result.schemesEvaluation mustBe List(SchemeEvaluationResult(SchemeId(commercial), Red.toString))
    }

    // scheme | c1 | c2 | c3 | c4 | overall | expected result
    // s1     | A  | G  | G  | G  | G       | A
    "evaluate to Amber with a single Amber and the others Green" in {
      val schemes = List(SchemeId(commercial))

      val passMarkSettings = AssessmentCentrePassMarkSettings(List(
        AssessmentCentrePassMark(SchemeId(commercial), AssessmentCentrePassMarkThresholds(
          seeingTheBigPicture = PassMarkThreshold(1.0, 3.0),
          makingEffectiveDecisions = PassMarkThreshold(1.0, 3.0),
          communicatingAndInfluencing = PassMarkThreshold(1.0, 3.0),
          workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(8.0, 11.3)))), // overall score = 11.3 so this should evaluate to Green
        version = "v1", createDate = DateTime.now(), "user")

      val candidateScores = AssessmentScoresAllExercises(applicationId,
        writtenExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(2.0),
            makingEffectiveDecisionsAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(3.1),
            updatedBy = updatedBy
          )),
        teamExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            makingEffectiveDecisionsAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(3.1),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          )),
        leadershipExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(2.0),
            communicatingAndInfluencingAverage = Some(3.1),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          ))
      )

      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, schemes, candidateScores)
      val result = evaluationEngine.evaluate(candidateScore)
      result.schemesEvaluation mustBe List(SchemeEvaluationResult(SchemeId(commercial), Amber.toString))
    }

    // scheme | c1 | c2 | c3 | c4 | overall | expected result
    // s1     | G  | G  | G  | G  | G       | G
    "evaluate to Green when all are Green" in {
      val schemes = List(SchemeId(commercial))

      val passMarkSettings = AssessmentCentrePassMarkSettings(List(
        AssessmentCentrePassMark(SchemeId(commercial), AssessmentCentrePassMarkThresholds(
          seeingTheBigPicture = PassMarkThreshold(1.0, 3.0),
          makingEffectiveDecisions = PassMarkThreshold(1.0, 3.0),
          communicatingAndInfluencing = PassMarkThreshold(1.0, 3.0),
          workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(8.0, 12.0)))),
        version = "v1", createDate = DateTime.now(), "user")

      val candidateScores = AssessmentScoresAllExercises(applicationId,
        writtenExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(3.0),
            makingEffectiveDecisionsAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(3.1),
            updatedBy = updatedBy
          )),
        teamExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            makingEffectiveDecisionsAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(3.1),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          )),
        leadershipExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(3.0),
            communicatingAndInfluencingAverage = Some(3.1),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          ))
      )

      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, schemes, candidateScores)
      val result = evaluationEngine.evaluate(candidateScore)
      result.schemesEvaluation mustBe List(SchemeEvaluationResult(SchemeId(commercial), Green.toString))
    }

    // scheme | c1 | c2 | c3 | c4 | overall | expected result
    // s1     | A  | G  | R  | G  | G       | R
    "evaluate to Red with a mix of Amber, Green, Red" in {
      val schemes = List(SchemeId(commercial))

      val passMarkSettings = AssessmentCentrePassMarkSettings(List(
        AssessmentCentrePassMark(SchemeId(commercial), AssessmentCentrePassMarkThresholds(
          seeingTheBigPicture = PassMarkThreshold(1.0, 3.0),
          makingEffectiveDecisions = PassMarkThreshold(1.0, 3.0),
          communicatingAndInfluencing = PassMarkThreshold(1.0, 3.0),
          workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(8.0, 9.0)))),
        version = "v1", createDate = DateTime.now(), "user")

      val candidateScores = AssessmentScoresAllExercises(applicationId,
        writtenExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(2.0),
            makingEffectiveDecisionsAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(0.99),
            updatedBy = updatedBy
          )),
        teamExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            makingEffectiveDecisionsAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(0.99),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          )),
        leadershipExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(2.0),
            communicatingAndInfluencingAverage = Some(0.99),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          ))
      )

      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, schemes, candidateScores)
      val result = evaluationEngine.evaluate(candidateScore)
      result.schemesEvaluation mustBe List(SchemeEvaluationResult(SchemeId(commercial), Red.toString))
    }

    // scheme | c1 | c2 | c3 | c4 | overall | expected result
    // s1     | G  | G  | G  | G  | G       | G
    "evaluate to Green when zero pass marks are specified" in {
      val schemes = List(SchemeId(commercial))

      val passMarkSettings = AssessmentCentrePassMarkSettings(List(
        AssessmentCentrePassMark(SchemeId(commercial), AssessmentCentrePassMarkThresholds(
          seeingTheBigPicture = PassMarkThreshold(0.0, 0.0),
          makingEffectiveDecisions = PassMarkThreshold(0.0, 0.0),
          communicatingAndInfluencing = PassMarkThreshold(0.0, 0.0),
          workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0.0, 0.0),
          overall = PassMarkThreshold(0.0, 0.0)))),
        version = "v1", createDate = DateTime.now(), "user")

      val candidateScores = AssessmentScoresAllExercises(applicationId,
        writtenExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(1.0),
            makingEffectiveDecisionsAverage = Some(1.0),
            communicatingAndInfluencingAverage = Some(1.0),
            updatedBy = updatedBy
          )),
        teamExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            makingEffectiveDecisionsAverage = Some(1.0),
            communicatingAndInfluencingAverage = Some(1.0),
            workingTogetherDevelopingSelfAndOthersAverage = Some(1.0),
            updatedBy = updatedBy
          )),
        leadershipExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(1.0),
            communicatingAndInfluencingAverage = Some(1.0),
            workingTogetherDevelopingSelfAndOthersAverage = Some(1.0),
            updatedBy = updatedBy
          ))
      )

      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, schemes, candidateScores)
      val result = evaluationEngine.evaluate(candidateScore)
      result.schemesEvaluation mustBe List(SchemeEvaluationResult(SchemeId(commercial), Green.toString))
    }

    // scheme | c1 | c2 | c3 | c4 | overall | expected result
    // s1     | A  | G  | G  | G  | A       | A
    // s2     | A  | G  | G  | G  | A       | A
    // s3     | A  | G  | G  | G  | R       | R
    "evaluate multiple schemes to Amber or Red" in {
      // The pass marks which the evaluation engine uses to work out if each scheme has passed/failed
      val passMarkSettings = AssessmentCentrePassMarkSettings(List(
        AssessmentCentrePassMark(SchemeId(commercial), AssessmentCentrePassMarkThresholds(
          seeingTheBigPicture = PassMarkThreshold(1.0, 3.0),
          makingEffectiveDecisions = PassMarkThreshold(1.0, 3.0),
          communicatingAndInfluencing = PassMarkThreshold(1.0, 3.0),
          workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(10.0, 15.0))),
        AssessmentCentrePassMark(SchemeId(digitalDataTechnologyAndCyber), AssessmentCentrePassMarkThresholds(
          seeingTheBigPicture = PassMarkThreshold(1.0, 3.0),
          makingEffectiveDecisions = PassMarkThreshold(1.0, 3.0),
          communicatingAndInfluencing = PassMarkThreshold(1.0, 3.0),
          workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(10.0, 16.0))),
        AssessmentCentrePassMark(SchemeId(diplomaticService), AssessmentCentrePassMarkThresholds(
          seeingTheBigPicture = PassMarkThreshold(1.0, 3.0),
          makingEffectiveDecisions = PassMarkThreshold(1.0, 3.0),
          communicatingAndInfluencing = PassMarkThreshold(1.0, 3.0),
          workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(16.0, 20.0)))),
        version = "v1", DateTime.now(), "user")

      val candidateScores = AssessmentScoresAllExercises(applicationId,
        writtenExercise = Some(
          AssessmentScoresExercise(makingEffectiveDecisionsAverage = Some(2.5), communicatingAndInfluencingAverage = Some(3.5),
            seeingTheBigPictureAverage = Some(4.0), updatedBy = updatedBy, attended = true
          )),
        teamExercise = Some(
          AssessmentScoresExercise(makingEffectiveDecisionsAverage = Some(3.0), workingTogetherDevelopingSelfAndOthersAverage = Some(4.5),
            communicatingAndInfluencingAverage = Some(3.5), updatedBy = updatedBy, attended = true
          )),
        leadershipExercise = Some(
          AssessmentScoresExercise(workingTogetherDevelopingSelfAndOthersAverage = Some(3.0), communicatingAndInfluencingAverage = Some(3.5),
            seeingTheBigPictureAverage = Some(4.0), updatedBy = updatedBy, attended = true
          ))
      )

      // List of schemes for which the candidate will be evaluated
      val candidateSchemes = List(SchemeId(commercial), SchemeId(digitalDataTechnologyAndCyber), SchemeId(diplomaticService))
      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, candidateSchemes, candidateScores)

      val result = evaluationEngine.evaluate(candidateScore)
      result.schemesEvaluation mustBe List(
        SchemeEvaluationResult(SchemeId(commercial), Amber.toString),
        SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), Amber.toString),
        SchemeEvaluationResult(SchemeId(diplomaticService), Red.toString)
      )

      val expectedCompetencyAverage = CompetencyAverageResult(
        makingEffectiveDecisionsAverage = 2.75,
        workingTogetherDevelopingSelfAndOthersAverage = 3.75,
        communicatingAndInfluencingAverage = 3.5,
        seeingTheBigPictureAverage = 4.0,
        overallScore = 14.0
      )
      result.competencyAverageResult mustBe expectedCompetencyAverage
    }
  }
}
