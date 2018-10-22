/*
 * Copyright 2018 HM Revenue & Customs
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
import model.command.SetTScoreRequest
import model.exchange.campaignmanagement.{ AfterDeadlineSignupCode, AfterDeadlineSignupCodeUnused }
import model.persisted._
import org.joda.time.DateTime
import repositories._
import repositories.application.{ GeneralApplicationMongoRepository, GeneralApplicationRepository }
import repositories.campaignmanagement.CampaignManagementAfterDeadlineSignupCodeRepository
import repositories.onlinetesting.{ Phase1TestRepository, Phase2TestRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CampaignManagementService extends CampaignManagementService{
  val afterDeadlineCodeRepository: CampaignManagementAfterDeadlineSignupCodeRepository = campaignManagementAfterDeadlineSignupCodeRepository
  val uuidFactory = UUIDFactory
  val appRepo: GeneralApplicationMongoRepository = applicationRepository
  val phase1TestRepo: Phase1TestRepository = phase1TestRepository
  val phase2TestRepo: Phase2TestRepository = phase2TestRepository
}

trait CampaignManagementService {
  val afterDeadlineCodeRepository: CampaignManagementAfterDeadlineSignupCodeRepository
  val uuidFactory: UUIDFactory
  val appRepo: GeneralApplicationRepository
  val phase1TestRepo: Phase1TestRepository
  val phase2TestRepo: Phase2TestRepository

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

  def removeCandidate(applicationId: String): Future[Unit] = {
    appRepo.removeCandidate(applicationId)
  }

  private def verifyPhase1TestScoreData(tScoreRequest: SetTScoreRequest): Future[Boolean] = {
    for {
      phase1TestProfileOpt <- phase1TestRepo.getTestGroup(tScoreRequest.applicationId)
    } yield {
      val sjqBqPresent = phase1TestProfileOpt.exists { phase1TestProfile =>
        phase1TestProfile.tests.size == 2
      }

      val sjqScoresSaved = phase1TestProfileOpt.flatMap { phase1TestProfile =>
        phase1TestProfile.tests.head.testResult
      }.isDefined

      val bqScoresSaved = phase1TestProfileOpt.flatMap { phase1TestProfile =>
        phase1TestProfile.tests(1).testResult
      }.isDefined

      sjqBqPresent && sjqScoresSaved && bqScoresSaved
    }
  }

  private def updatePhase1TestProfile(tScoreRequest: SetTScoreRequest, phase1TestProfile: Phase1TestProfile): Phase1TestProfile = {
    phase1TestProfile.copy(tests = updateTests(tScoreRequest, phase1TestProfile.tests))
  }

  private def updateTests(tScoreRequest: SetTScoreRequest, tests: List[CubiksTest]) :List[CubiksTest] = {
    tests.map { test =>
      val testResultOpt = test.testResult.map { testResult =>
        testResult.copy(tScore = Some(tScoreRequest.tScore))
      }
      test.copy(testResult = testResultOpt)
    }
  }

  def setPhase1TScore(tScoreRequest: SetTScoreRequest): Future[Unit] = {
    (for {
      dataIsValid <- verifyPhase1TestScoreData(tScoreRequest)
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
      val etrayPresent = phase2TestProfileOpt.exists { phase2TestProfile =>
        phase2TestProfile.tests.size == 1
      }

      val etrayScoresSaved = phase2TestProfileOpt.flatMap { phase2TestProfile =>
        phase2TestProfile.tests.head.testResult
      }.isDefined

      etrayPresent && etrayScoresSaved
    }
  }

  private def updatePhase2TestGroup(tScoreRequest: SetTScoreRequest, phase2TestGroup: Phase2TestGroup): Phase2TestGroup = {
    phase2TestGroup.copy(tests = updateTests(tScoreRequest, phase2TestGroup.tests))
  }
}
