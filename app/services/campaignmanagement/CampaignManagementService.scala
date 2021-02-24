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

package services.campaignmanagement

import factories.UUIDFactory
import javax.inject.{ Inject, Singleton }
import model.command.SetTScoreRequest
import model.exchange.campaignmanagement.{ AfterDeadlineSignupCode, AfterDeadlineSignupCodeUnused }
import model.persisted._
import org.joda.time.DateTime
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.campaignmanagement.CampaignManagementAfterDeadlineSignupCodeRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CampaignManagementService @Inject() (afterDeadlineCodeRepository: CampaignManagementAfterDeadlineSignupCodeRepository,
                                           uuidFactory: UUIDFactory,
                                           appRepo: GeneralApplicationRepository,
                                           phase1TestRepo: Phase1TestRepository,
                                           phase2TestRepo: Phase2TestRepository,
                                           questionnaireRepo: QuestionnaireRepository,
                                           mediaRepo: MediaRepository,
                                           contactDetailsRepo: ContactDetailsRepository) {

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
      DateTime.now().plusHours(expiryInHours)
    )

    afterDeadlineCodeRepository.save(newCode).map { _ =>
      AfterDeadlineSignupCode(newCode.code)
    }
  }

  def listCollections: Future[String] = {
    appRepo.listCollections.map(_.mkString("\n"))
  }

  def removeCollection(name: String): Future[Unit] = {
    appRepo.removeCollection(name)
  }

  def removeCandidate(applicationId: String, userId: String): Future[Unit] = {
    for {
      _ <- appRepo.removeCandidate(applicationId)
      _ <- contactDetailsRepo.removeContactDetails(userId)
      _ <- mediaRepo.removeMedia(userId)
      _ <- questionnaireRepo.removeQuestions(applicationId)
    } yield ()
  }

  private def verifyPhase1TestScoreData(tScoreRequest: SetTScoreRequest, isGis: Boolean): Future[Boolean] = {
    for {
      phase1TestProfileOpt <- phase1TestRepo.getTestGroup(tScoreRequest.applicationId)
    } yield {
      val testsPresentWithResultsSaved = phase1TestProfileOpt.exists { phase1TestProfile =>
        val expectedNumberOfTests = if (isGis) { 2 } else { 4 }
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
}
