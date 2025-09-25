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

package services.assessmentcentre

import model.EvaluationResults._
import model.ProgressStatuses._
import model._
import model.assessmentscores.AssessmentScoresAllExercises
import model.command.{ApplicationForProgression, ApplicationStatusDetails}
import model.exchange.passmarksettings._
import model.persisted.SchemeEvaluationResult
import model.persisted.fsac.{AnalysisExercise, AssessmentCentreTests}
import play.api.libs.json.{Format, OFormat}
import repositories.{AssessmentScoresRepository, SchemeRepository}
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import services.assessmentcentre.AssessmentCentreService.CandidateAlreadyHasAnAnalysisExerciseException
import services.evaluation.AssessmentCentreEvaluationEngine
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import testkit.ScalaMockImplicits._
import testkit.ScalaMockUnitSpec

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AssessmentCentreServiceSpec extends ScalaMockUnitSpec with Schemes {

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
      (mockAssessmentCentreRepo.getTests _).expects("appId1").returningAsync(AssessmentCentreTests())
      (mockAssessmentCentreRepo.updateTests _).expects("appId1", assessmentCentreTestsWithTests).returningAsync

      whenReady(service.updateAnalysisTest("appId1", "fileId1")) { results =>
         results mustBe unit
      }
    }

    "do not update when submissions are already present" in new TestFixture {
      (mockAssessmentCentreRepo.getTests _).expects("appId1").returningAsync(assessmentCentreTestsWithTests)

      whenReady(service.updateAnalysisTest("appId1", "fileId1").failed) { result =>
        result mustBe a[CandidateAlreadyHasAnAnalysisExerciseException]
      }
    }
  }

  "next assessment candidate" should {
    "return an assessment candidate score with application Id" in new ReturnPassMarksFixture {
      (mockAssessmentCentreRepo.nextApplicationReadyForAssessmentScoreEvaluation _)
        .expects(*, *)
        .returning(Future.successful(List(applicationId)))

      (mockAssessmentScoresRepo.find _)
        .expects(*)
        .returning(Future.successful(Some(AssessmentScoresAllExercises(applicationId))))

      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(*)
        .returning(Future.successful(List(SchemeEvaluationResult(schemeId = Commercial, result = Green.toString))))

      val results = service.nextAssessmentCandidatesReadyForEvaluation(batchSize).futureValue

      results must not be empty
      results.foreach { result =>
        result.passmark mustBe passMarkSettings
        result.schemes mustBe List(Commercial)
        result.scores.applicationId mustBe applicationId
      }
    }

    "withdrawn schemes should be not be evaluated" in new ReturnPassMarksFixture {
      val datScheme = Digital
      val currentSchemeStatus = List(
        SchemeEvaluationResult(schemeId = Commercial, result = Green.toString),
        SchemeEvaluationResult(schemeId = datScheme, result = Withdrawn.toString)
      )
      (mockAssessmentCentreRepo.nextApplicationReadyForAssessmentScoreEvaluation _)
        .expects(*, *)
        .returning(Future.successful(List(applicationId)))

      (mockAssessmentScoresRepo.find _)
        .expects(*)
        .returning(Future.successful(Some(AssessmentScoresAllExercises(applicationId))))

      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(*)
        .returning(Future.successful(currentSchemeStatus))

      val results = service.nextAssessmentCandidatesReadyForEvaluation(batchSize).futureValue

      results must not be empty
      results.foreach { result =>
        result.passmark mustBe passMarkSettings
        result.schemes mustBe List(Commercial)
        result.scores.applicationId mustBe applicationId
      }
    }

    "sdip scheme should be not be evaluated for sdip faststream candidates" in new ReturnPassMarksFixture {
      val sdipScheme = Sdip
      val datScheme = Digital
      val currentSchemeStatus = List(
        SchemeEvaluationResult(schemeId = sdipScheme, result = Green.toString),
        SchemeEvaluationResult(schemeId = datScheme, result = Green.toString)
      )
      (mockAssessmentCentreRepo.nextApplicationReadyForAssessmentScoreEvaluation _)
        .expects(*, *)
        .returning(Future.successful(List(applicationId)))

      (mockAssessmentScoresRepo.find _)
        .expects(*)
        .returning(Future.successful(Some(AssessmentScoresAllExercises(applicationId))))

      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(*)
        .returning(Future.successful(currentSchemeStatus))

      val results = service.nextAssessmentCandidatesReadyForEvaluation(batchSize).futureValue

      results must not be empty
      results.foreach { result =>
        result.passmark mustBe passMarkSettings
        result.schemes mustBe List(datScheme)
        result.scores.applicationId mustBe applicationId
      }
    }

    "return none if there is no passmark settings set" in new TestFixture {
      implicit val jsonFormat: OFormat[AssessmentCentrePassMarkSettingsPersistence] = AssessmentCentrePassMarkSettingsPersistence.jsonFormat
      (mockAssessmentCentrePassMarkSettingsService.getLatestPassMarkSettings(_: Format[AssessmentCentrePassMarkSettingsPersistence])).expects(*)
        .returning(Future.successful(None))

      val result = service.nextAssessmentCandidatesReadyForEvaluation(batchSize).futureValue
      result mustBe empty
    }

    "return none if there is no application ready for assessment score evaluation" in new ReturnPassMarksFixture {
      (mockAssessmentCentreRepo.nextApplicationReadyForAssessmentScoreEvaluation _)
        .expects(*, *)
        .returning(Future.successful(Nil))

      val result = service.nextAssessmentCandidatesReadyForEvaluation(batchSize).futureValue
      result mustBe empty
    }
  }

  "evaluate assessment scores" should {
    "write back schemes that have failed in a previous evaluation and sdip scheme and current status amber updated to green" in new TestFixture {
      // The current scheme status (the common area that represents the current status of each scheme)
      val currentSchemeStatus = List(SchemeEvaluationResult(Commercial, Amber.toString),
        SchemeEvaluationResult(Digital, Red.toString),
        SchemeEvaluationResult(Sdip, Green.toString))
      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(applicationId.toString())
        .returning(Future.successful(currentSchemeStatus))

      // The schemes that have been evaluated during a previous evaluation
      (mockAssessmentCentreRepo.getFsacEvaluatedSchemes _)
        .expects(applicationId.toString())
        .returningAsync(Some(Seq(
          SchemeEvaluationResult(Commercial, Amber.toString),
          SchemeEvaluationResult(Digital, Red.toString)))
        )

      // This is the result of current evaluation (excludes failed schemes, withdrawn schemes and sdip from previous evaluation)
      val schemeEvaluationResult = List(SchemeEvaluationResult(Commercial, Green.toString))
      val evaluationResult = AssessmentEvaluationResult(fsacResults, schemeEvaluationResult)

      (mockEvaluationEngine.evaluate _)
        .expects(*)
        .returning(evaluationResult)

      (mockAppRepo.findStatus _)
        .expects(applicationId.toString(), true)
        .returningAsync(scoresAcceptedApplicationStatusDetails)

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _)
        .expects(applicationId.toString(), ASSESSMENT_CENTRE_PASSED)
        .returning(Future.successful(()))

      val newSchemeStatus = List(SchemeEvaluationResult(Commercial, Green.toString),
        SchemeEvaluationResult(Digital, Red.toString),
        SchemeEvaluationResult(Sdip, Green.toString))
      // The merged evaluation is the current evaluation plus any failed schemes from previous evaluation
      val mergedEvaluationResult = List(
        SchemeEvaluationResult(Commercial, Green.toString),
        SchemeEvaluationResult(Digital, Red.toString)
      )
      val expectedEvaluation = AssessmentPassMarkEvaluation(applicationId, "1", AssessmentEvaluationResult(
        fsacResults, mergedEvaluationResult))

      (mockAssessmentCentreRepo.saveAssessmentScoreEvaluation _)
        .expects(expectedEvaluation, newSchemeStatus)
        .returning(Future.successful(()))

      val assessmentData = AssessmentPassMarksSchemesAndScores(passmark = passMarkSettings, schemes = List(Commercial),
        scores = AssessmentScoresAllExercises(applicationId = applicationId))
      service.evaluateAssessmentCandidate(assessmentData).futureValue
    }

    "move sdip faststream candidate to ASSESSMENT_CENTRE_FAILED_SDIP_GREEN when he fails all faststream schemes " +
      "but sdip is green" in new TestFixture {
      val schemeEvaluationResult = List(SchemeEvaluationResult(Commercial, Red.toString))
      val evaluationResult = AssessmentEvaluationResult(fsacResults, schemeEvaluationResult)

      (mockEvaluationEngine.evaluate _)
        .expects(*)
        .returning(evaluationResult)

      (mockAppRepo.findStatus _)
        .expects(applicationId.toString(), true)
        .returningAsync(sdipFaststreamScoresAcceptedApplicationStatusDetails)

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _)
        .expects(applicationId.toString(), ASSESSMENT_CENTRE_FAILED_SDIP_GREEN)
        .returning(Future.successful(()))

      val currentSchemeStatus = List(SchemeEvaluationResult(Commercial, Green.toString),
        SchemeEvaluationResult(Sdip, Green.toString))
      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(applicationId.toString())
        .returning(Future.successful(currentSchemeStatus))

      val newSchemeStatus = List(SchemeEvaluationResult(Commercial, Red.toString),
        SchemeEvaluationResult(Sdip, Green.toString))
      val expectedEvaluation = AssessmentPassMarkEvaluation(applicationId, "1", AssessmentEvaluationResult(
        fsacResults, schemeEvaluationResult))

      (mockAssessmentCentreRepo.getFsacEvaluatedSchemes _)
        .expects(applicationId.toString())
        .returningAsync(None)

      (mockAssessmentCentreRepo.saveAssessmentScoreEvaluation _)
        .expects(expectedEvaluation, newSchemeStatus)
        .returning(Future.successful(()))

      val assessmentData = AssessmentPassMarksSchemesAndScores(passmark = passMarkSettings, schemes = List(Commercial),
        scores = AssessmentScoresAllExercises(applicationId = applicationId))
      service.evaluateAssessmentCandidate(assessmentData).futureValue
    }

    "move sdip faststream candidate to ASSESSMENT_CENTRE_FAILED when he fails all faststream schemes " +
      "and has already withdrawn from sdip" in new TestFixture {
      val schemeEvaluationResult = List(SchemeEvaluationResult(Commercial, Red.toString))
      val evaluationResult = AssessmentEvaluationResult(fsacResults, schemeEvaluationResult)

      (mockEvaluationEngine.evaluate _)
        .expects(*)
        .returning(evaluationResult)

      (mockAppRepo.findStatus _)
        .expects(applicationId.toString(), true)
        .returningAsync(sdipFaststreamScoresAcceptedApplicationStatusDetails)

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _)
        .expects(applicationId.toString(), ASSESSMENT_CENTRE_FAILED)
        .returning(Future.successful(()))

      val currentSchemeStatus = List(SchemeEvaluationResult(Commercial, Green.toString),
        SchemeEvaluationResult(Sdip, Withdrawn.toString))
      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(applicationId.toString())
        .returning(Future.successful(currentSchemeStatus))

      val newSchemeStatus = List(SchemeEvaluationResult(Commercial, Red.toString),
        SchemeEvaluationResult(Sdip, Withdrawn.toString))
      val expectedEvaluation = AssessmentPassMarkEvaluation(applicationId, "1", AssessmentEvaluationResult(
        fsacResults, schemeEvaluationResult))

      (mockAssessmentCentreRepo.getFsacEvaluatedSchemes _)
        .expects(applicationId.toString())
        .returningAsync(None)

      (mockAssessmentCentreRepo.saveAssessmentScoreEvaluation _)
        .expects(expectedEvaluation, newSchemeStatus)
        .returning(Future.successful(()))

      val assessmentData = AssessmentPassMarksSchemesAndScores(passmark = passMarkSettings, schemes = List(Commercial),
        scores = AssessmentScoresAllExercises(applicationId = applicationId))
      service.evaluateAssessmentCandidate(assessmentData).futureValue
    }

    "save evaluation result to red with current status green updated to red" in new TestFixture {
      val schemeEvaluationResult = List(SchemeEvaluationResult(Commercial, Red.toString))
      val evaluationResult = AssessmentEvaluationResult(fsacResults, schemeEvaluationResult)

      (mockEvaluationEngine.evaluate _)
        .expects(*)
        .returning(evaluationResult)

      (mockAppRepo.findStatus _)
        .expects(applicationId.toString(), true)
        .returningAsync(scoresAcceptedApplicationStatusDetails)

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _)
        .expects(applicationId.toString(), ASSESSMENT_CENTRE_FAILED)
        .returning(Future.successful(()))

      val currentSchemeStatus = List(SchemeEvaluationResult(Commercial, Green.toString),
        SchemeEvaluationResult(Digital, Red.toString))
      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(applicationId.toString())
        .returning(Future.successful(currentSchemeStatus))

      val newSchemeStatus = List(SchemeEvaluationResult(Commercial, Red.toString),
        SchemeEvaluationResult(Digital, Red.toString))
      val expectedEvaluation = AssessmentPassMarkEvaluation(applicationId, "1", AssessmentEvaluationResult(
        fsacResults, schemeEvaluationResult))

      (mockAssessmentCentreRepo.getFsacEvaluatedSchemes _)
        .expects(applicationId.toString())
        .returningAsync(None)

      (mockAssessmentCentreRepo.saveAssessmentScoreEvaluation _)
        .expects(expectedEvaluation, newSchemeStatus)
        .returning(Future.successful(()))

      val assessmentData = AssessmentPassMarksSchemesAndScores(passmark = passMarkSettings, schemes = List(Commercial),
        scores = AssessmentScoresAllExercises(applicationId = applicationId))
      service.evaluateAssessmentCandidate(assessmentData).futureValue
    }

    "save evaluation result to green with current status green remains the same" in new TestFixture {
      val schemeEvaluationResult = List(SchemeEvaluationResult(Commercial, Green.toString))
      val evaluationResult = AssessmentEvaluationResult(fsacResults, schemeEvaluationResult)

      (mockEvaluationEngine.evaluate _)
        .expects(*)
        .returning(evaluationResult)

      (mockAppRepo.findStatus _)
        .expects(applicationId.toString(), true)
        .returningAsync(scoresAcceptedApplicationStatusDetails)

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _)
        .expects(applicationId.toString(), ASSESSMENT_CENTRE_PASSED)
        .returning(Future.successful(()))

      val currentSchemeStatus = List(SchemeEvaluationResult(Commercial, Green.toString),
        SchemeEvaluationResult(Digital, Red.toString))
      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(applicationId.toString())
        .returning(Future.successful(currentSchemeStatus))

      val newSchemeStatus = List(SchemeEvaluationResult(Commercial, Green.toString),
        SchemeEvaluationResult(Digital, Red.toString))
      val expectedEvaluation = AssessmentPassMarkEvaluation(applicationId, "1", AssessmentEvaluationResult(
        fsacResults, schemeEvaluationResult))

      (mockAssessmentCentreRepo.getFsacEvaluatedSchemes _)
        .expects(applicationId.toString())
        .returningAsync(None)

      (mockAssessmentCentreRepo.saveAssessmentScoreEvaluation _)
        .expects(expectedEvaluation, newSchemeStatus)
        .returning(Future.successful(()))

      val assessmentData = AssessmentPassMarksSchemesAndScores(passmark = passMarkSettings, schemes = List(Commercial),
        scores = AssessmentScoresAllExercises(applicationId = applicationId))
      service.evaluateAssessmentCandidate(assessmentData).futureValue
    }

    "save evaluation result to red with current status green updated to red and current scheme status containing ambers" in new TestFixture {
      val schemeEvaluationResult = List(SchemeEvaluationResult(Commercial, Red.toString))
      val evaluationResult = AssessmentEvaluationResult(fsacResults, schemeEvaluationResult)

      (mockEvaluationEngine.evaluate _)
        .expects(*)
        .returning(evaluationResult)

      (mockAppRepo.findStatus _)
        .expects(applicationId.toString(), true)
        .returningAsync(scoresAcceptedApplicationStatusDetails)

      val currentSchemeStatus = List(SchemeEvaluationResult(Commercial, Green.toString),
        SchemeEvaluationResult(Digital, Amber.toString))
      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(applicationId.toString())
        .returning(Future.successful(currentSchemeStatus))

      val newSchemeStatus = List(SchemeEvaluationResult(Commercial, Red.toString),
        SchemeEvaluationResult(Digital, Amber.toString))
      val expectedEvaluation = AssessmentPassMarkEvaluation(applicationId, "1", AssessmentEvaluationResult(
        fsacResults, schemeEvaluationResult))

      (mockAssessmentCentreRepo.getFsacEvaluatedSchemes _)
        .expects(applicationId.toString())
        .returningAsync(None)

      (mockAssessmentCentreRepo.saveAssessmentScoreEvaluation _)
        .expects(expectedEvaluation, newSchemeStatus)
        .returning(Future.successful(()))

      val assessmentData = AssessmentPassMarksSchemesAndScores(passmark = passMarkSettings, schemes = List(Commercial),
        scores = AssessmentScoresAllExercises(applicationId = applicationId))
      service.evaluateAssessmentCandidate(assessmentData).futureValue
    }

    "mark candidate for job offer after fsac pass marks change" in new TestFixture {
      // This is running after the 1st scheme has been withdrawn and after the pass marks
      // have changed so the result is the next scheme pref goes from Amber to Green
      val schemeEvaluationResult = List(SchemeEvaluationResult(Digital, Green.toString))
      val evaluationResult = AssessmentEvaluationResult(fsacResults, schemeEvaluationResult)

      (mockEvaluationEngine.evaluate _)
        .expects(*)
        .returning(evaluationResult)

      val fsbApplicationStatusDetails = ApplicationStatusDetails(
        ApplicationStatus.FSB,
        ApplicationRoute.Faststream,
        Some(FSB_AWAITING_ALLOCATION),
        statusDate = None,
        overrideSubmissionDeadline = None
      )

      (mockAppRepo.findStatus _)
        .expects(applicationId.toString(), true)
        .returningAsync(fsbApplicationStatusDetails)

      val currentSchemeStatus = List(SchemeEvaluationResult(Commercial, Withdrawn.toString), // This needs a fsb
        SchemeEvaluationResult(Digital, Amber.toString)) // This does not need a fsb
      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(applicationId.toString())
        .returning(Future.successful(currentSchemeStatus))

      // This is the scheme evaluation stored in the db from the last time the candidate was evaluated
      (mockAssessmentCentreRepo.getFsacEvaluatedSchemes _)
        .expects(applicationId.toString())
        .returningAsync(
          Some(
            List(
              SchemeEvaluationResult(Commercial, Green.toString),
              SchemeEvaluationResult(Digital, Amber.toString)
            )
          )
        )

      val expectedEvaluation = AssessmentPassMarkEvaluation(
        applicationId, "1",
        AssessmentEvaluationResult(fsacResults,
          schemesEvaluation = List(
            SchemeEvaluationResult(Commercial, Green.toString),
            SchemeEvaluationResult(Digital, Green.toString)
          )
        )
      )
      val newCurrentSchemeStatus = List(
        SchemeEvaluationResult(Commercial, Withdrawn.toString),
        SchemeEvaluationResult(Digital, Green.toString)
      )

      (mockAssessmentCentreRepo.saveAssessmentScoreEvaluation _)
        .expects(expectedEvaluation, newCurrentSchemeStatus)
        .returning(Future.successful(()))

      (mockSchemeRepository.schemeRequiresFsb _)
        .expects(Digital)
        .returning(false)

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _)
        .expects(applicationId.toString(), ProgressStatuses.FSB_FSAC_REEVALUATION_JOB_OFFER)
        .returningAsync

      val assessmentData = AssessmentPassMarksSchemesAndScores(
        passmark = passMarkSettings, schemes = List(Commercial),
        scores = AssessmentScoresAllExercises(applicationId = applicationId)
      )
      // When this is called all the Reds or Withdrawn schemes in the css have been removed and are not evaluated at fsac
      service.evaluateAssessmentCandidate(assessmentData).futureValue
    }

    "not mark candidate for job offer after fsac pass marks change if the 1st residual preference needs a fsb" in new TestFixture {
      // This is running after the 1st scheme has been withdrawn and after the pass marks
      // have changed so the result is the next scheme pref goes from Amber to Green
      val schemeEvaluationResult = List(SchemeEvaluationResult(Property, Green.toString))
      val evaluationResult = AssessmentEvaluationResult(fsacResults, schemeEvaluationResult)

      (mockEvaluationEngine.evaluate _)
        .expects(*)
        .returning(evaluationResult)

      val fsbApplicationStatusDetails = ApplicationStatusDetails(
        ApplicationStatus.FSB,
        ApplicationRoute.Faststream,
        Some(FSB_AWAITING_ALLOCATION),
        statusDate = None,
        overrideSubmissionDeadline = None
      )

      (mockAppRepo.findStatus _)
        .expects(applicationId.toString(), true)
        .returningAsync(fsbApplicationStatusDetails)

      val currentSchemeStatus = List(SchemeEvaluationResult(Commercial, Withdrawn.toString), // This needs a fsb
        SchemeEvaluationResult(Property, Amber.toString)) // This needs a fsb
      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(applicationId.toString())
        .returning(Future.successful(currentSchemeStatus))

      // This is the scheme evaluation stored in the db from the last time the candidate was evaluated
      (mockAssessmentCentreRepo.getFsacEvaluatedSchemes _)
        .expects(applicationId.toString())
        .returningAsync(
          Some(
            List(
              SchemeEvaluationResult(Commercial, Green.toString),
              SchemeEvaluationResult(Property, Amber.toString)
            )
          )
        )

      val expectedEvaluation = AssessmentPassMarkEvaluation(
        applicationId, "1",
        AssessmentEvaluationResult(fsacResults,
          schemesEvaluation = List(
            SchemeEvaluationResult(Commercial, Green.toString),
            SchemeEvaluationResult(Property, Green.toString)
          )
        )
      )
      val newCurrentSchemeStatus = List(
        SchemeEvaluationResult(Commercial, Withdrawn.toString),
        SchemeEvaluationResult(Property, Green.toString)
      )

      (mockAssessmentCentreRepo.saveAssessmentScoreEvaluation _)
        .expects(expectedEvaluation, newCurrentSchemeStatus)
        .returning(Future.successful(()))

      (mockSchemeRepository.schemeRequiresFsb _)
        .expects(Property)
        .returning(true)

      val assessmentData = AssessmentPassMarksSchemesAndScores(
        passmark = passMarkSettings, schemes = List(Commercial),
        scores = AssessmentScoresAllExercises(applicationId = applicationId)
      )
      // When this is called all the Reds or Withdrawn schemes in the css have been removed and are not evaluated at fsac
      service.evaluateAssessmentCandidate(assessmentData).futureValue
    }

    "fail fsb candidate after fsac pass marks change and all schemes are Red or Withdrawn" in new TestFixture {
      // The situation here is the 1st scheme was Green and the 2nd Amber after the evaluation ran the first time.
      // Candidate is moved to Fsb for the 1st scheme.
      // Candidate then withdraws the 1st scheme and we change the pass marks.
      // The result is the next scheme pref goes from Amber to Red so the candidate should be failed
      val schemeEvaluationResult = List(SchemeEvaluationResult(Digital, Red.toString))
      val evaluationResult = AssessmentEvaluationResult(fsacResults, schemeEvaluationResult)

      (mockEvaluationEngine.evaluate _)
        .expects(*)
        .returning(evaluationResult)

      val fsbApplicationStatusDetails = ApplicationStatusDetails(
        ApplicationStatus.FSB,
        ApplicationRoute.Faststream,
        Some(FSB_AWAITING_ALLOCATION),
        statusDate = None,
        overrideSubmissionDeadline = None
      )

      (mockAppRepo.findStatus _)
        .expects(applicationId.toString(), true)
        .returningAsync(fsbApplicationStatusDetails)

      val currentSchemeStatus = List(SchemeEvaluationResult(Commercial, Withdrawn.toString), // This needs a fsb
        SchemeEvaluationResult(Digital, Amber.toString)) // This does not need a fsb
      (mockAppRepo.getCurrentSchemeStatus _)
        .expects(applicationId.toString())
        .returning(Future.successful(currentSchemeStatus))

      // This is the scheme evaluation stored in the db from the last time the candidate was evaluated
      (mockAssessmentCentreRepo.getFsacEvaluatedSchemes _)
        .expects(applicationId.toString())
        .returningAsync(
          Some(
            List(
              SchemeEvaluationResult(Commercial, Green.toString),
              SchemeEvaluationResult(Digital, Amber.toString)
            )
          )
        )

      val expectedEvaluation = AssessmentPassMarkEvaluation(
        applicationId, "1",
        AssessmentEvaluationResult(fsacResults,
          schemesEvaluation = List(
            SchemeEvaluationResult(Commercial, Green.toString),
            SchemeEvaluationResult(Digital, Red.toString)
          )
        )
      )
      val newCurrentSchemeStatus = List(
        SchemeEvaluationResult(Commercial, Withdrawn.toString),
        SchemeEvaluationResult(Digital, Red.toString)
      )

      (mockAssessmentCentreRepo.saveAssessmentScoreEvaluation _)
        .expects(expectedEvaluation, newCurrentSchemeStatus)
        .returning(Future.successful(()))

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _)
        .expects(applicationId.toString(), ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)
        .returningAsync

      val assessmentData = AssessmentPassMarksSchemesAndScores(
        passmark = passMarkSettings, schemes = List(Digital),
        scores = AssessmentScoresAllExercises(applicationId = applicationId)
      )
      // When this is called all the Reds or Withdrawn schemes in the css have been removed and are not evaluated at fsac
      service.evaluateAssessmentCandidate(assessmentData).futureValue
    }
  }

  trait TestFixture {
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockAssessmentCentreRepo = mock[AssessmentCentreRepository]
//    val mockAssessmentCentrePassMarkSettingsService = mock[PassMarkSettingsService[AssessmentCentrePassMarkSettings]]
    val mockAssessmentCentrePassMarkSettingsService = mock[AssessmentCentrePassMarkSettingsService]
    val mockAssessmentScoresRepo = mock[AssessmentScoresRepository]
    val mockEvaluationEngine = mock[AssessmentCentreEvaluationEngine]
    val mockSchemeRepository = mock[SchemeRepository]
    val batchSize = 1
/*
    val service = new AssessmentCentreService {
      val applicationRepo: GeneralApplicationRepository = mockAppRepo
      val assessmentCentreRepo: AssessmentCentreRepository = mockAssessmentCentreRepo
      val passmarkService: PassMarkSettingsService[AssessmentCentrePassMarkSettings] = mockAssessmentCentrePassMarkSettingsService
      val assessmentScoresRepo: AssessmentScoresRepository = mockAssessmentScoresRepo
      val evaluationEngine: AssessmentCentreEvaluationEngine = mockEvaluationEngine
    }*/

    val service = new AssessmentCentreService(
      mockAppRepo,
      mockAssessmentCentreRepo,
      mockAssessmentCentrePassMarkSettingsService,
      mockAssessmentScoresRepo,
      mockSchemeRepository,
      mockEvaluationEngine
    )

    val applicationsToProgressToSift = List(
      ApplicationForProgression("appId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
        List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString))),
      ApplicationForProgression("appId2", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
        List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString))),
      ApplicationForProgression("appId3", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
        List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))
    )

    val scoresAcceptedApplicationStatusDetails = ApplicationStatusDetails(
      ApplicationStatus.ASSESSMENT_CENTRE,
      ApplicationRoute.Faststream,
      Some(ASSESSMENT_CENTRE_SCORES_ACCEPTED),
      statusDate = None,
      overrideSubmissionDeadline = None
    )

    val sdipFaststreamScoresAcceptedApplicationStatusDetails = scoresAcceptedApplicationStatusDetails.copy(
      applicationRoute = ApplicationRoute.SdipFaststream
    )

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

    val passMarkSettings = AssessmentCentrePassMarkSettingsPersistence(List(
      AssessmentCentreExercisePassMark(Commercial, AssessmentCentreExercisePassMarkThresholds(
        exercise1 = PassMarkThreshold(1.0, 3.0),
        exercise2 = PassMarkThreshold(1.0, 3.0),
        exercise3 = PassMarkThreshold(1.0, 3.0),
        overall = PassMarkThreshold(10.0, 15.0))),
      AssessmentCentreExercisePassMark(Digital, AssessmentCentreExercisePassMarkThresholds(
        exercise1 = PassMarkThreshold(1.0, 3.0),
        exercise2 = PassMarkThreshold(1.0, 3.0),
        exercise3 = PassMarkThreshold(1.0, 3.0),
        overall = PassMarkThreshold(10.0, 15.0))),
      AssessmentCentreExercisePassMark(DiplomaticAndDevelopment, AssessmentCentreExercisePassMarkThresholds(
        exercise1 = PassMarkThreshold(1.0, 3.0),
        exercise2 = PassMarkThreshold(1.0, 3.0),
        exercise3 = PassMarkThreshold(1.0, 3.0),
        overall = PassMarkThreshold(10.0, 15.0)))),
      "1", OffsetDateTime.now, "user")

    val exerciseAverageResult = ExerciseAverageResult(
      exercise1Average = 4.0,
      exercise2Average = 4.0,
      exercise3Average = 4.0,
      overallScore = 12.0
    )
    val fsacResults = FsacResults(exerciseAverageResult)
  }

  trait ReturnPassMarksFixture extends TestFixture {
    implicit val jsonFormat: OFormat[AssessmentCentrePassMarkSettings] = AssessmentCentrePassMarkSettings.jsonFormat
    (mockAssessmentCentrePassMarkSettingsService.getLatestPassMarkSettings(_: Format[AssessmentCentrePassMarkSettingsPersistence])).expects(*)
      .returning(Future.successful(Some(passMarkSettings)))
  }
}
