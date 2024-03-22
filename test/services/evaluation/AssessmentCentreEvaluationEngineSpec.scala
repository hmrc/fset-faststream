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

import model.EvaluationResults._
import model.assessmentscores.{AssessmentScoresAllExercises, AssessmentScoresExercise}
import model.exchange.passmarksettings._
import model.persisted.SchemeEvaluationResult
import model.{AssessmentPassMarksSchemesAndScores, Schemes, UniqueIdentifier}
import services.BaseServiceSpec

import java.time.OffsetDateTime

class AssessmentCentreEvaluationEngineSpec extends BaseServiceSpec with Schemes {

  val applicationId = UniqueIdentifier.randomUniqueIdentifier
  val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  val evaluationEngine = new AssessmentCentreEvaluationEngineImpl

  "Assessment Centre Passmark Rules engine evaluation" should {

    // scheme | e1 | e2 | e3 | overall | expected result
    // s1     | R  | G  | G  | G       | R
    // e1       e2       e3       overall
    // f    p   f    p   f    p   f    p
    // 2.5  3.0 1.0  3.0 1.0  3.0 6.0  8.0
    //  2.3967   3.1      3.1      8.5967
    //   R        G        G          G
    "evaluate to Red with a single Red and the others Green" in {
      val schemes = List(Commercial)

      val passMarkSettings = AssessmentCentrePassMarkSettingsPersistence(List(
        AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
          writtenExercise = PassMarkThreshold(2.5, 3.0),
          teamExercise = PassMarkThreshold(1.0, 3.0),
          leadershipExercise = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(6.0, 8.0)))),
        version = "v1", createDate = OffsetDateTime.now, "user")

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
            seeingTheBigPictureAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(3.1),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          ))
      )

      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, schemes, candidateScores)
      val result = evaluationEngine.evaluate(candidateScore)
      result.schemesEvaluation mustBe List(SchemeEvaluationResult(Commercial, Red.toString))
    }

    // scheme | e1 | e2 | e3 | overall | expected result
    // s1     | A  | G  | G  | G       | A
    // e1       e2       e3       overall
    // f    p   f    p   f    p   f    p
    // 1.0  3.0 1.0  3.0 1.0  3.0 6.0  8.0
    //  2.7333   3.1      3.1      8.9333
    //   R        G        G          G
    "evaluate to Amber with a single Amber and the others Green" in {
      val schemes = List(Commercial)

      val passMarkSettings = AssessmentCentrePassMarkSettingsPersistence(List(
        AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
          writtenExercise = PassMarkThreshold(1.0, 3.0),
          teamExercise = PassMarkThreshold(1.0, 3.0),
          leadershipExercise = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(6.0, 8.9)))),
        version = "v1", createDate = OffsetDateTime.now, "user")

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
            seeingTheBigPictureAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(3.1),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          ))
      )

      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, schemes, candidateScores)
      val result = evaluationEngine.evaluate(candidateScore)
      result.schemesEvaluation mustBe List(SchemeEvaluationResult(Commercial, Amber.toString))
    }

    // scheme | e1 | e2 | e3 | overall | expected result
    // s1     | G  | G  | G  | G       | G
    // e1       e2       e3       overall
    // f    p   f    p   f    p   f    p
    // 1.0  3.0 1.0  3.0 1.0  3.0 8.0  9.3
    //  3.1      3.1      3.1      9.3
    //   G        G        G          G
    "evaluate to Green when all are Green" in {
      val schemes = List(Commercial)

      val passMarkSettings = AssessmentCentrePassMarkSettingsPersistence(List(
        AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
          writtenExercise = PassMarkThreshold(1.0, 3.0),
          teamExercise = PassMarkThreshold(1.0, 3.0),
          leadershipExercise = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(8.0, 9.3)))),
        version = "v1", createDate = OffsetDateTime.now, "user")

      val candidateScores = AssessmentScoresAllExercises(applicationId,
        writtenExercise = Some(
          AssessmentScoresExercise(
            attended = true,
            seeingTheBigPictureAverage = Some(3.1),
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
            seeingTheBigPictureAverage = Some(3.1),
            communicatingAndInfluencingAverage = Some(3.1),
            workingTogetherDevelopingSelfAndOthersAverage = Some(3.1),
            updatedBy = updatedBy
          ))
      )

      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, schemes, candidateScores)
      val result = evaluationEngine.evaluate(candidateScore)
      result.schemesEvaluation mustBe List(SchemeEvaluationResult(Commercial, Green.toString))
    }

    // scheme | e1 | e2 | e3 | overall | expected result
    // s1     | A  | G  | R  | G       | R
    // e1       e2       e3       overall
    // f    p   f    p   f    p   f    p
    // 1.0  3.0 1.0  3.0 2.1  3.0 6.0  7.1
    //  2.03     3.1      2.03     7.16
    //   A        G        G          G
    "evaluate to Red with a mix of Amber, Green, Red" in {
      val schemes = List(Commercial)

      val passMarkSettings = AssessmentCentrePassMarkSettingsPersistence(List(
        AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
          writtenExercise = PassMarkThreshold(1.0, 3.0),
          teamExercise = PassMarkThreshold(1.0, 3.0),
          leadershipExercise = PassMarkThreshold(2.1, 3.0),
          overall = PassMarkThreshold(6.0, 7.16)))),
        version = "v1", createDate = OffsetDateTime.now, "user")

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
            communicatingAndInfluencingAverage = Some(3.1),
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
      result.schemesEvaluation mustBe List(SchemeEvaluationResult(Commercial, Red.toString))
    }

    // scheme | e1 | e2 | e3 | overall | expected result
    // s1     | G  | G  | G  | G       | G
    "evaluate to Green when zero pass marks are specified" in {
      val schemes = List(Commercial)

      val passMarkSettings = AssessmentCentrePassMarkSettingsPersistence(List(
        AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
          writtenExercise = PassMarkThreshold(0.0, 0.0),
          teamExercise = PassMarkThreshold(0.0, 0.0),
          leadershipExercise = PassMarkThreshold(0.0, 0.0),
          overall = PassMarkThreshold(0.0, 0.0)))),
        version = "v1", createDate = OffsetDateTime.now, "user")

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
      result.schemesEvaluation mustBe List(SchemeEvaluationResult(Commercial, Green.toString))
    }

    // scheme | e1 | e2 | e3 | overall | expected result
    // s1     | A  | G  | G  | A       | A
    // s2     | A  | G  | G  | A       | A
    // s3     | A  | G  | G  | R       | R
    "evaluate multiple schemes to Amber or Red" in {
      // The pass marks which the evaluation engine uses to work out if each scheme has passed/failed
      val passMarkSettings = AssessmentCentrePassMarkSettingsPersistence(List(
        AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
          writtenExercise = PassMarkThreshold(1.0, 3.4),
          teamExercise = PassMarkThreshold(1.0, 3.0),
          leadershipExercise = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(10.0, 11.0))),
        AssessmentCentreExercisePassMark(DigitalDataTechnologyAndCyber, AssessmentCentreExercisePassMarkThresholds(
          writtenExercise = PassMarkThreshold(1.0, 3.4),
          teamExercise = PassMarkThreshold(1.0, 3.0),
          leadershipExercise = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(10.0, 11.0))),
        AssessmentCentreExercisePassMark(DiplomaticAndDevelopment, AssessmentCentreExercisePassMarkThresholds(
          writtenExercise = PassMarkThreshold(1.0, 3.4),
          teamExercise = PassMarkThreshold(1.0, 3.0),
          leadershipExercise = PassMarkThreshold(1.0, 3.0),
          overall = PassMarkThreshold(10.6, 12.0)))),
        version = "v1", OffsetDateTime.now, "user")

      val candidateScores = AssessmentScoresAllExercises(applicationId,
        writtenExercise = Some(
          AssessmentScoresExercise(makingEffectiveDecisionsAverage = Some(2.5), communicatingAndInfluencingAverage = Some(3.5),
            seeingTheBigPictureAverage = Some(4.0), updatedBy = updatedBy, attended = true
            // Avg = (2.5 + 3.5 + 4.0) / 3 = 10 / 3 = 3.3333
          )),
        teamExercise = Some(
          AssessmentScoresExercise(makingEffectiveDecisionsAverage = Some(3.0), workingTogetherDevelopingSelfAndOthersAverage = Some(4.5),
            communicatingAndInfluencingAverage = Some(3.5), updatedBy = updatedBy, attended = true
            // Avg = (3.0 + 4.5 + 3.5) / 3 = 11 / 3 = 3.6667
          )),
        leadershipExercise = Some(
          AssessmentScoresExercise(workingTogetherDevelopingSelfAndOthersAverage = Some(3.0), communicatingAndInfluencingAverage = Some(3.5),
            seeingTheBigPictureAverage = Some(4.0), updatedBy = updatedBy, attended = true
            // (Avg = 3.0 + 3.5 + 4.0) / 3 = 10.5 / 3 = 3.5
          ))
      )

      // List of schemes for which the candidate will be evaluated
      val candidateSchemes = List(Commercial, DigitalDataTechnologyAndCyber, DiplomaticAndDevelopment)
      val candidateScore = AssessmentPassMarksSchemesAndScores(passMarkSettings, candidateSchemes, candidateScores)

      val result = evaluationEngine.evaluate(candidateScore)
      result.schemesEvaluation mustBe List(
        SchemeEvaluationResult(Commercial, Amber.toString),
        SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Amber.toString),
        SchemeEvaluationResult(DiplomaticAndDevelopment, Red.toString)
      )

      val expectedCompetencyAverage = CompetencyAverageResult(
        makingEffectiveDecisionsAverage = 2.75,
        workingTogetherDevelopingSelfAndOthersAverage = 3.75,
        communicatingAndInfluencingAverage = 3.5,
        seeingTheBigPictureAverage = 4.0,
        overallScore = 14.0
      )
      val expectedExerciseAverage = ExerciseAverageResult(
        writtenExerciseAverage = 3.3333,
        teamExerciseAverage = 3.6667,
        leadershipExerciseAverage = 3.5,
        overallScore = 10.5
      )
      result.fsacResults.competencyAverageResult mustBe expectedCompetencyAverage
      result.fsacResults.exerciseAverageResult mustBe expectedExerciseAverage
    }
  }
}
