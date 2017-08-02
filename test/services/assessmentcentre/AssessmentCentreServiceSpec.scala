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

package services.assessmentcentre

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.EvaluationResults.{ CompetencyAverageResult, AssessmentEvaluationResult, Green }
import model._
import model.assessmentscores.AssessmentScoresAllExercises
import model.command.ApplicationForFsac
import model.exchange.passmarksettings._
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import model.persisted.fsac.{ AnalysisExercise, AssessmentCentreTests }
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.{ OneAppPerSuite, PlaySpec }
import play.api.libs.json.Format
import play.api.mvc.Results
import repositories.AssessmentScoresRepository
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.{ AssessmentCentreRepository, CurrentSchemeStatusRepository }
import services.evaluation.AssessmentCentreEvaluationEngine
import services.passmarksettings.PassMarkSettingsService
import services.assessmentcentre.AssessmentCentreService.CandidateAlreadyHasAnAnalysisExerciseException
import testkit.{ ExtendedTimeout, FutureHelper }

import scala.concurrent.Future

class AssessmentCentreServiceSpec extends PlaySpec with OneAppPerSuite with Results with ScalaFutures with FutureHelper with MockFactory
  with ExtendedTimeout {

  "progress candidates to assessment centre" must {
    "progress candidates to assessment centre, attempting all despite errors" in new TestFixture {
      progressToAssessmentCentreMocks
      whenReady(service.progressApplicationsToAssessmentCentre(applicationsToProgressToSift)) {
        results =>
          val failedApplications = Seq(applicationsToProgressToSift(1))
          val passedApplications = Seq(applicationsToProgressToSift.head, applicationsToProgressToSift(2))
          results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }
  }

  "getTests" must {
    "call the assessment centre repository" in new TestFixture {
      (mockAssessmentCentreRepo.getTests _).expects("appId1").returning(Future.successful(AssessmentCentreTests()))

      whenReady(service.getTests("appId1")) { results =>
          results mustBe AssessmentCentreTests()
      }
    }
  }

  "updateAnalysisTest" must {
    val assessmentCentreTestsWithTests = AssessmentCentreTests(
      Some(AnalysisExercise(
        "fileId1"
      ))
    )

    "update when submissions are not present" in new TestFixture {
      (mockAssessmentCentreRepo.getTests _).expects("appId1").returning(Future.successful(AssessmentCentreTests()))
      (mockAssessmentCentreRepo.updateTests _).expects("appId1", assessmentCentreTestsWithTests).returning(Future.successful(()))

      whenReady(service.updateAnalysisTest("appId1", "fileId1")) { results =>
         results mustBe (())
      }
    }

    "do not update when submissions are already present" in new TestFixture {
      (mockAssessmentCentreRepo.getTests _).expects("appId1").returning(Future.successful(
        assessmentCentreTestsWithTests
      ))

      whenReady(service.updateAnalysisTest("appId1", "fileId1").failed) { result =>
        result mustBe a[CandidateAlreadyHasAnAnalysisExerciseException]
      }
    }
  }

  "next assessment candidate" should {
    "return an assessment candidate score with application Id" in new ReturnPassMarksFixture {
      (mockAssessmentCentreRepo.nextApplicationReadyForAssessmentScoreEvaluation _)
        .expects(*)
        .returning(Future.successful(Some(applicationId.toString())))

      (mockAssessmentScoresRepo.find _)
        .expects(*)
        .returning(Future.successful(Some(AssessmentScoresAllExercises(applicationId))))

      (mockCurrentSchemeStatusRepo.getTestGroup _)
        .expects(*)
        .returning(Future.successful(
          Some(Phase3TestGroup(
            expirationDate = DateTime.now,
            tests = List.empty[LaunchpadTest],
            evaluation = Some(
              PassmarkEvaluation(passmarkVersion = "version1",
                previousPhasePassMarkVersion = None,
                result = List(
                  SchemeEvaluationResult(schemeId = SchemeId("Commercial"), result = Green.toString)
                ),
                resultVersion = "version1",
                previousPhaseResultVersion = None)
            )
          ))
      ))

      val result = service.nextAssessmentCandidateReadyForEvaluation.futureValue

      result must not be empty
      result.get.passmark mustBe passMarkSettings
      result.get.schemes mustBe List(SchemeId("Commercial"))
      result.get.scores.applicationId mustBe applicationId
    }

    "return none if there is no passmark settings set" in new TestFixture {
      implicit val jsonFormat = AssessmentCentrePassMarkSettings.jsonFormat
      (mockAssessmentCentrePassMarkSettingsService.getLatestPassMarkSettings(_: Format[AssessmentCentrePassMarkSettings])).expects(*)
        .returning(Future.successful(None))


      val result = service.nextAssessmentCandidateReadyForEvaluation.futureValue
      result mustBe empty
    }

    "return none if there is no application ready for assessment score evaluation" in new ReturnPassMarksFixture {
      (mockAssessmentCentreRepo.nextApplicationReadyForAssessmentScoreEvaluation _)
        .expects(*)
        .returning(Future.successful(None))

      val result = service.nextAssessmentCandidateReadyForEvaluation.futureValue
      result mustBe empty
    }
  }

  "evaluate assessment scores" should {
    "save passed evaluation result" in new TestFixture {
      val competencyAverageResult = CompetencyAverageResult(
        analysisAndDecisionMakingAverage = 4.0,
        buildingProductiveRelationshipsAverage = 4.0,
        leadingAndCommunicatingAverage = 4.0,
        strategicApproachToObjectivesAverage = 4.0,
        overallScore = 16.0
      )

      val schemeEvaluationResult = List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString))
      val evaluationResult = AssessmentEvaluationResult(
        passedMinimumCompetencyLevel = Some(true), competencyAverageResult, schemeEvaluationResult)

      (mockEvaluationEngine.evaluate _)
        .expects(*, *)
        .returning(evaluationResult)

      val expected = AssessmentPassMarkEvaluation(applicationId, "1", AssessmentEvaluationResult(
        passedMinimumCompetencyLevel = Some(true), competencyAverageResult, schemeEvaluationResult))

      (mockAssessmentCentreRepo.saveAssessmentScoreEvaluation _)
        .expects(expected)
        .returning(Future.successful(()))

      val assessmentData = AssessmentPassMarksSchemesAndScores(passmark = passMarkSettings, schemes = List(SchemeId("Commercial")),
        scores = AssessmentScoresAllExercises(applicationId = applicationId))
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = false, None)
      service.evaluateAssessmentCandidate(assessmentData, config).futureValue
    }
  }

  trait TestFixture {
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockAssessmentCentreRepo = mock[AssessmentCentreRepository]
    val mockAssessmentCentrePassMarkSettingsService = mock[PassMarkSettingsService[AssessmentCentrePassMarkSettings]]
    val mockAssessmentScoresRepo = mock[AssessmentScoresRepository]
    val mockCurrentSchemeStatusRepo = mock[CurrentSchemeStatusRepository]
    val mockEvaluationEngine = mock[AssessmentCentreEvaluationEngine]

    val service = new AssessmentCentreService {
      val applicationRepo: GeneralApplicationRepository = mockAppRepo
      val assessmentCentreRepo: AssessmentCentreRepository = mockAssessmentCentreRepo
      val passmarkService: PassMarkSettingsService[AssessmentCentrePassMarkSettings] = mockAssessmentCentrePassMarkSettingsService
      val assessmentScoresRepo: AssessmentScoresRepository = mockAssessmentScoresRepo
      val currentSchemeStatusRepo: CurrentSchemeStatusRepository = mockCurrentSchemeStatusRepo
      val evaluationEngine: AssessmentCentreEvaluationEngine = mockEvaluationEngine
    }

    val applicationsToProgressToSift = List(
      ApplicationForFsac("appId1", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)), "", Some("")), Nil),
      ApplicationForFsac("appId2", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)), "", Some("")), Nil),
      ApplicationForFsac("appId3", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)), "", Some("")), Nil))

    def progressToAssessmentCentreMocks = {
      (mockAssessmentCentreRepo.progressToAssessmentCentre _)
        .expects(applicationsToProgressToSift.head, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
        .returning(Future.successful(()))
      (mockAssessmentCentreRepo.progressToAssessmentCentre _)
        .expects(applicationsToProgressToSift(1), ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
        .returning(Future.failed(new Exception))
      (mockAssessmentCentreRepo.progressToAssessmentCentre _)
        .expects(applicationsToProgressToSift(2), ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
        .returning(Future.successful(()))
    }

    val applicationId = UniqueIdentifier.randomUniqueIdentifier

    val passMarkSettings = AssessmentCentrePassMarkSettings(List(
      AssessmentCentrePassMark(SchemeId("Commercial"), AssessmentCentrePassMarkThresholds(PassMarkThreshold(10.0, 15.0))),
      AssessmentCentrePassMark(SchemeId("DigitalAndTechnology"), AssessmentCentrePassMarkThresholds(PassMarkThreshold(10.0, 15.0))),
      AssessmentCentrePassMark(SchemeId("DiplomaticService"), AssessmentCentrePassMarkThresholds(PassMarkThreshold(10.0, 15.0)))),
      "1", DateTime.now(), "user")
  }

  trait ReturnPassMarksFixture extends TestFixture {
    implicit val jsonFormat = AssessmentCentrePassMarkSettings.jsonFormat
    (mockAssessmentCentrePassMarkSettingsService.getLatestPassMarkSettings(_: Format[AssessmentCentrePassMarkSettings])).expects(*)
      .returning(Future.successful(Some(passMarkSettings)))
  }
}
