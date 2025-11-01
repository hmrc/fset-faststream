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

package services.onlinetesting.phase1

import model.command.{Phase1ScoreUpdateRequest, Phase1ScoreUpdateResponse}
import model.persisted.*
import repositories.*
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.*
import Phase1ScoresBulkUpdateService.{ApplicationNotFoundException, NoTestFoundException, TestScoresException}

import java.time.OffsetDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Phase1ScoresBulkUpdateService @Inject()(appRepo: GeneralApplicationRepository,
                                              phase1TestRepo: Phase1TestRepository)(implicit ec: ExecutionContext) {

  def updatePhase1Scores(scoreRequests: Seq[Phase1ScoreUpdateRequest]): Future[Seq[Phase1ScoreUpdateResponse]] = {
    Future.sequence(scoreRequests.map { scoreRequest =>
      (for {
        _ <- checkApplicationFound(scoreRequest)
        scoreDataIsValid <- verifyNumberOfTestsAndTestResults(scoreRequest)
        phase1TestProfileOpt <- phase1TestRepo.getTestGroup(scoreRequest.applicationId)
        _ <- phase1TestRepo.insertOrUpdateTestGroup(
          scoreRequest.applicationId,
          updatePhase1TestProfile(
            scoreRequest,
            phase1TestProfileOpt.getOrElse(throw new IllegalStateException("Failed to find Phase1TestProfile"))
          )
        )
      } yield {
        Phase1ScoreUpdateResponse(scoreRequest, status = "Successfully updated")
      }).recover {
        case e: ApplicationNotFoundException =>
          Phase1ScoreUpdateResponse(scoreRequest, status = e.getMessage)
        case e: TestScoresException =>
          Phase1ScoreUpdateResponse(scoreRequest, status = e.getMessage)
        case e: NoTestFoundException =>
          Phase1ScoreUpdateResponse(scoreRequest, status = e.getMessage)
        case e: IllegalStateException =>
          Phase1ScoreUpdateResponse(scoreRequest, status = e.getMessage)
      }
    })
  }

  private def checkApplicationFound(scoreRequest: Phase1ScoreUpdateRequest): Future[Unit] = {
    for {
      candidateOpt <- appRepo.find(scoreRequest.applicationId)
    } yield {
      if (candidateOpt.isEmpty) {
        throw ApplicationNotFoundException(s"No application found for ${scoreRequest.applicationId}")
      }
    }
  }

  private def verifyNumberOfTestsAndTestResults(scoreRequest: Phase1ScoreUpdateRequest): Future[Boolean] = {
    for {
      phase1TestProfileOpt <- phase1TestRepo.getTestGroup(scoreRequest.applicationId)
    } yield {
      val testsPresentWithResultsSaved = phase1TestProfileOpt.exists { phase1TestProfile =>
        val expectedNumberOfTests = 2
        val allTestsPresent = phase1TestProfile.activeTests.size == expectedNumberOfTests
        if (!allTestsPresent) {
          throw TestScoresException(s"Expected $expectedNumberOfTests tests but only found ${phase1TestProfile.activeTests.size}")
        }
        val allTestsHaveATestResult = phase1TestProfile.activeTests.forall(_.testResult.isDefined)
        if (!allTestsHaveATestResult) {
          throw TestScoresException(s"Not all tests have a test result")
        }
        // Iterate the collection of active tests and check each one for inventoryId and orderId
        val inventoryAndOrderIdsFound = phase1TestProfile.activeTests.exists { test =>
          test.inventoryId == scoreRequest.inventoryId && test.orderId == scoreRequest.orderId
        }
        if (!inventoryAndOrderIdsFound) {
          throw NoTestFoundException(s"No test found for inventoryId=${scoreRequest.inventoryId} and orderId = ${scoreRequest.orderId}")
        }
        allTestsPresent && allTestsHaveATestResult
      }
      testsPresentWithResultsSaved
    }
  }

  private def updatePhase1TestProfile(scoreRequest: Phase1ScoreUpdateRequest, phase1TestProfile: Phase1TestProfile): Phase1TestProfile = {
    phase1TestProfile.copy(tests = updateTest(scoreRequest, phase1TestProfile.tests))
  }

  private def updateTest(scoreRequest: Phase1ScoreUpdateRequest, tests: List[PsiTest]) :List[PsiTest] = {
    if (tests.count(t => scoreRequest.inventoryId.contains(t.inventoryId)) != 1 ||
      tests.count(t => scoreRequest.orderId.contains(t.orderId)) != 1) {
      throw NoTestFoundException(
        s"No test found with applicationId=${scoreRequest.applicationId}," +
          s"inventoryId=${scoreRequest.inventoryId},orderId=${scoreRequest.orderId} " +
          "in Phase1")
    }
    tests.map { test =>
      val testResultOpt = test.testResult.map { testResult =>
        if (scoreRequest.inventoryId.contains(test.inventoryId)) {
          testResult.copy(tScore = scoreRequest.tScore, rawScore = scoreRequest.rawScore)
        } else {
          testResult
        }
      }
      test.copy(testResult = testResultOpt)
    }
  }
}

object Phase1ScoresBulkUpdateService {
  case class NoTestFoundException(msg: String) extends Exception(msg)
  case class ApplicationNotFoundException(msg: String) extends Exception(msg)
  case class TestScoresException(msg: String) extends Exception(msg)
}
