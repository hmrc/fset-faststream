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

package services.campaignmanagement

import connectors.AuthProviderClient
import factories.UUIDFactory
import model.Exceptions.NotFoundException
import model.UniqueIdentifier
import model.command.{ProgressResponse, SetTScoreRequest}
import model.exchange.campaignmanagement.{AfterDeadlineSignupCode, AfterDeadlineSignupCodeUnused}
import model.persisted.*
import model.persisted.fsac.AssessmentCentreTests
import model.persisted.sift.SiftAnswers
import play.api.Logging
import repositories.*
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.campaignmanagement.CampaignManagementAfterDeadlineSignupCodeRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fileupload.FileUploadRepository
import repositories.onlinetesting.*
import repositories.sift.SiftAnswersRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{OffsetDateTime, ZoneId}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CampaignManagementService @Inject() (afterDeadlineCodeRepository: CampaignManagementAfterDeadlineSignupCodeRepository,
                                           uuidFactory: UUIDFactory,
                                           appRepo: GeneralApplicationRepository,
                                           phase1TestRepo: Phase1TestRepository,
                                           phase2TestRepo: Phase2TestRepository,
                                           questionnaireRepo: QuestionnaireRepository,
                                           mediaRepo: MediaRepository,
                                           contactDetailsRepo: ContactDetailsRepository,
                                           siftAnswersRepo: SiftAnswersRepository,
                                           assessorAssessmentScoresRepo: AssessorAssessmentScoresMongoRepository,
                                           reviewerAssessmentScoresRepo: ReviewerAssessmentScoresMongoRepository,
                                           assessmentCentreRepo: AssessmentCentreRepository,
                                           fileUploadRepo: FileUploadRepository,
                                           candidateAllocationRepository: CandidateAllocationRepository,
                                           authProviderClient: AuthProviderClient)(implicit ec: ExecutionContext) extends Logging {

  def afterDeadlineSignupCodeUnusedAndValid(code: String): Future[AfterDeadlineSignupCodeUnused] = {
    afterDeadlineCodeRepository.findUnusedValidCode(code).map(storedCodeOpt =>
      AfterDeadlineSignupCodeUnused(storedCodeOpt.isDefined, storedCodeOpt.map(_.expires))
    )
  }

  def markSignupCodeAsUsed(code: String, applicationId: String): Future[Unit] = {
    afterDeadlineCodeRepository.markSignupCodeAsUsed(code, applicationId).map(_ => ())
  }

  def generateAfterDeadlineSignupCode(createdByUserId: String, expiryInHours: Int): Future[AfterDeadlineSignupCode] = {
    val newCode = CampaignManagementAfterDeadlineCode(
      uuidFactory.generateUUID(),
      createdByUserId,
      OffsetDateTime.now(ZoneId.of("UTC")).plusHours(expiryInHours)
    )

    afterDeadlineCodeRepository.save(newCode).map { _ =>
      AfterDeadlineSignupCode(newCode.code)
    }
  }

  def listCollections: Future[String] = {
    appRepo.listCollections.map(_.mkString("\n"))
  }

  def removeCollection(name: String): Future[Either[Exception, Unit]] = {
    appRepo.removeCollection(name)
  }

  def removeActivationRecords()(implicit hc: HeaderCarrier): Future[Unit] = {
    authProviderClient.removeAllActivationDocuments()
  }

  def removeResetPasswordRecords()(implicit hc: HeaderCarrier): Future[Unit] = {
    authProviderClient.removeAllResetPasswordDocuments()
  }

  def removeCandidate(applicationId: String, userId: String): Future[Unit] = {
    for {
      progress <- appRepo.findProgress(applicationId)
      // Fetch this before we remove the document from the application collection because that is where the data lives
      assessmentCentreTests <- assessmentCentreRepo.getTests(applicationId)
      // Record is created as soon as account is created
      _ <- removeCandidate(applicationId)
      // Record is created as soon as account is created
      _ <- removeMedia(userId)
      // Record is created after submitting page 1 personal details
      _ <- removeContactDetails(userId, progress)
      // Record is created after submitting page 4 Before you continue (diversity questions)
      _ <- removeQuestions(applicationId, progress)
      // Just check to see if any sift answers have been saved
      siftAnswersOpt <- siftAnswersRepo.findSiftAnswers(applicationId)
      // If the candidate didn't have any schemes needing to be sifted then there will be no data to delete here
      _ <- removeSiftAnswers(applicationId, progress, siftAnswersOpt)
      // fsac assessor scores
      _ <- removeFsacAssessorScores(applicationId, assessorAssessmentScoresRepo, progress)
      // fsac reviewer scores
      _ <- removeFsacReviewerScores(applicationId, reviewerAssessmentScoresRepo, progress)
      // uploaded document
      _ <- removeFsacDocument(applicationId, progress, assessmentCentreTests)
      // fsac events or fsb events
      _ <- removeCandidateAllocations(applicationId, progress)
    } yield ()
  }

  private def removeCandidate(applicationId: String) = {
    for {
      _ <- appRepo.removeCandidate(applicationId)
    } yield {
      logger.warn(s"Candidate deletion - successfully deleted application for applicationId: $applicationId")
    }
  }

  private def removeMedia(userId: String) = {
    for {
      _ <- mediaRepo.removeMedia(userId)
    } yield {
      logger.warn(s"Candidate deletion - successfully deleted media for userId: $userId")
    }
  }

  private def removeContactDetails(userId: String, progress: ProgressResponse) = {
    (for {
      _ <- contactDetailsRepo.removeContactDetails(userId)
    } yield {
      logger.warn(s"Candidate deletion - successfully deleted contact details for userId: $userId")
    }).recover {
      case e: NotFoundException =>
        if (progress.personalDetails) {
          logger.warn(s"Candidate deletion - contact details have been filled in but none found so will throw NotFoundException")
          throw e
        } else {
          // Just swallow because the candidate hasn't filled in personal details
          logger.warn(s"Candidate deletion - ${e.getMessage} and none expected")
          ()
        }
    }
  }

  private def removeQuestions(applicationId: String, progress: ProgressResponse) = {
    (for {
      _ <- questionnaireRepo.removeQuestions(applicationId)
    } yield {
      logger.warn(s"Candidate deletion - successfully deleted questions for applicationId: $applicationId")
    }).recover {
      case e: NotFoundException =>
        if (progress.questionnaire.nonEmpty) {
          logger.warn(s"Candidate deletion - questionnaire has been filled in but none found so will throw NotFoundException")
          throw e
        } else {
          // Just swallow because the candidate hasn't filled in any diversity info
          logger.warn(s"Candidate deletion - ${e.getMessage} and none expected")
          ()
        }
    }
  }

  private def removeSiftAnswers(applicationId: String, progress: ProgressResponse, siftAnswersOpt: Option[SiftAnswers]) = {
    (for {
      _ <- siftAnswersRepo.removeSiftAnswers(applicationId)
    } yield {
      logger.warn(s"Candidate deletion - successfully deleted sift answers for applicationId: $applicationId")
    }).recover {
      case e: NotFoundException =>
        if (progress.siftProgressResponse.siftEntered && siftAnswersOpt.nonEmpty) {
          logger.warn(s"Candidate deletion - sift answers have been filled in but none found so will throw NotFoundException")
          throw e
        } else {
          // Just swallow because the candidate doesn't have any schemes that need to be sifted
          logger.warn(s"Candidate deletion - ${e.getMessage} and none expected")
          ()
        }
    }
  }

  private def removeFsacAssessorScores(applicationId: String,
                                       assessorAssessmentScoresRepo: AssessorAssessmentScoresMongoRepository,
                                       progress: ProgressResponse) = {
    (for {
      _ <- assessorAssessmentScoresRepo.removeScores(UniqueIdentifier(applicationId))
    } yield {
      logger.warn(s"Candidate deletion - successfully deleted fsac assessor scores for applicationId: $applicationId")
    }).recover {
      case e: NotFoundException =>
        if (progress.assessmentCentre.scoresEntered) {
          throw e
        } else {
          // Just swallow because the candidate doesn't have assessor fsac scores
          logger.warn(s"Candidate deletion - ${e.getMessage} and none expected")
          ()
        }
    }
  }

  private def removeFsacReviewerScores(applicationId: String,
                                       reviewerAssessmentScoresRepo: ReviewerAssessmentScoresMongoRepository,
                                       progress: ProgressResponse) = {
    (for {
      _ <- reviewerAssessmentScoresRepo.removeScores(UniqueIdentifier(applicationId))
    } yield {
      logger.warn(s"Candidate deletion - successfully deleted fsac reviewer scores for applicationId: $applicationId")
    }).recover {
      case e: NotFoundException =>
        if (progress.assessmentCentre.scoresAccepted) {
          throw e
        } else {
          // Just swallow because the candidate doesn't have reviewer fsac scores
          logger.warn(s"Candidate deletion - ${e.getMessage} and none expected")
          ()
        }
    }
  }

  private def removeFsacDocument(applicationId: String,
                                 progress: ProgressResponse,
                                 assessmentCentreTests: AssessmentCentreTests) = {
    val uploadedDocumentFileIdOpt = assessmentCentreTests.analysisExercise.map(_.fileId)
    (for {
      metaDataOpt <- fileUploadRepo.retrieveMetaData(uploadedDocumentFileIdOpt.getOrElse(""))
      _ <- fileUploadRepo.deleteDocument(metaDataOpt.map(md => md._id).getOrElse(""), applicationId)
    } yield {
      logger.warn(s"Candidate deletion - successfully deleted fsac uploaded document for applicationId: $applicationId")
    }).recover {
      case e @ (_: NotFoundException | _: IllegalArgumentException) =>
        if (progress.assessmentCentre.allocationConfirmed && uploadedDocumentFileIdOpt.isDefined) {
          throw e
        } else {
          // Just swallow because the candidate doesn't have an uploaded fsac document
          logger.warn(s"Candidate deletion - ${e.getMessage} and none expected")
          ()
        }
    }
  }

  private def removeCandidateAllocations(applicationId: String, progress: ProgressResponse) = {
    (for {
      _ <- candidateAllocationRepository.deleteAllocations(applicationId)
    } yield {
      logger.warn(s"Candidate deletion - successfully deleted candidate allocations for applicationId: $applicationId")
    }).recover {
      case e: NotFoundException =>
        if (progress.assessmentCentre.allocationConfirmed || progress.fsb.allocationConfirmed) {
          throw e
        } else {
          // Just swallow because the candidate hasn't been allocated to any fsac or fsb events
          logger.warn(s"Candidate deletion - ${e.getMessage} and none expected")
          ()
        }
    }
  }

  private def verifyPhase1TestScoreData(tScoreRequest: SetTScoreRequest, isGis: Boolean): Future[Boolean] = {
    for {
      phase1TestProfileOpt <- phase1TestRepo.getTestGroup(tScoreRequest.applicationId)
    } yield {
      val testsPresentWithResultsSaved = phase1TestProfileOpt.exists { phase1TestProfile =>
        val expectedNumberOfTests = if (isGis) { 2 } else { 2 } // Leave it like this so we keep the distinction between Gis and regular
        val allTestsPresent = phase1TestProfile.activeTests.size == expectedNumberOfTests
        val allTestsHaveATestResult = phase1TestProfile.activeTests.forall(_.testResult.isDefined)
        allTestsPresent && allTestsHaveATestResult
      }
      testsPresentWithResultsSaved
    }
  }

  private def updatePhase1TestProfile(tScoreRequest: SetTScoreRequest, phase1TestProfile: Phase1TestProfile): Phase1TestProfile = {
    tScoreRequest.inventoryId match {
      case Some(_) =>
        phase1TestProfile.copy(tests = updateTest(tScoreRequest, phase1TestProfile.tests))
      case None =>
        phase1TestProfile.copy(tests = updateTests(tScoreRequest, phase1TestProfile.tests))
    }
  }

  private def updateTests(tScoreRequest: SetTScoreRequest, tests: List[PsiTest]) :List[PsiTest] = {
    tests.map { test =>
      val testResultOpt = test.testResult.map { testResult =>
        testResult.copy(tScore = tScoreRequest.tScore)
      }
      test.copy(testResult = testResultOpt)
    }
  }

  private def updateTest(tScoreRequest: SetTScoreRequest, tests: List[PsiTest]) :List[PsiTest] = {
    if (tests.count(t => tScoreRequest.inventoryId.contains(t.inventoryId)) != 1) {
      throw new Exception(s"No test found with applicationId=${tScoreRequest.applicationId},inventoryId=${tScoreRequest.inventoryId} " +
        s"in phase ${tScoreRequest.phase}")
    }
    tests.map { test =>
      val testResultOpt = test.testResult.map { testResult =>
        if (tScoreRequest.inventoryId.contains(test.inventoryId)) {
          testResult.copy(tScore = tScoreRequest.tScore)
        } else {
          testResult
        }
      }
      test.copy(testResult = testResultOpt)
    }
  }

  def setPhase1TScore(tScoreRequest: SetTScoreRequest): Future[Unit] = {
    (for {
      isGis <- appRepo.gisByApplication(tScoreRequest.applicationId)
      dataIsValid <- verifyPhase1TestScoreData(tScoreRequest, isGis)
    } yield {
      val msg = "Phase1 data is not in the correct state to set tScores"
      if (dataIsValid) {
        for {
          phase1TestProfileOpt <- phase1TestRepo.getTestGroup(tScoreRequest.applicationId)
          _ <- phase1TestRepo.insertOrUpdateTestGroup(
            tScoreRequest.applicationId, updatePhase1TestProfile(tScoreRequest, phase1TestProfileOpt
              .getOrElse(throw new IllegalStateException(msg))))
        } yield ()
      } else {
        throw new IllegalStateException(msg)
      }
    }).flatMap(identity)
  }

  def setPhase2TScore(tScoreRequest: SetTScoreRequest): Future[Unit] = {
    (for {
      dataIsValid <- verifyPhase2TestScoreData(tScoreRequest)
    } yield {
      val msg = "Phase2 data is not in the correct state to set tScores"
      if (dataIsValid) {
        for {
          phase2TestGroupOpt <- phase2TestRepo.getTestGroup(tScoreRequest.applicationId)
          _ <- phase2TestRepo.insertOrUpdateTestGroup(
            tScoreRequest.applicationId, updatePhase2TestGroup(tScoreRequest, phase2TestGroupOpt
              .getOrElse(throw new IllegalStateException(msg))))
        } yield ()
      } else {
        throw new IllegalStateException(msg)
      }
    }).flatMap(identity)
  }

  private def verifyPhase2TestScoreData(tScoreRequest: SetTScoreRequest): Future[Boolean] = {
    for {
      phase2TestProfileOpt <- phase2TestRepo.getTestGroup(tScoreRequest.applicationId)
    } yield {
      val testsPresentWithResultsSaved = phase2TestProfileOpt.exists { phase2TestProfile =>
        val allTestsPresent = phase2TestProfile.activeTests.size == 2
        val allTestsHaveATestResult = phase2TestProfile.activeTests.forall ( _.testResult.isDefined )
        allTestsPresent && allTestsHaveATestResult
      }
      testsPresentWithResultsSaved
    }
  }

  private def updatePhase2TestGroup(tScoreRequest: SetTScoreRequest, phase2TestGroup: Phase2TestGroup): Phase2TestGroup = {
    phase2TestGroup.copy(tests = updateTests(tScoreRequest, phase2TestGroup.tests))
  }

  def getUploadedDocumentId(applicationId: String): Future[AssessmentCentreTests] = {
    for {
      assessmentCentreTests <- assessmentCentreRepo.getTests(applicationId)
    } yield assessmentCentreTests
  }
}
