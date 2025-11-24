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
import services.onlinetesting.phase1.Phase1ScoresBulkUpdateService.{ApplicationNotFoundException, NoTestFoundException}

import java.time.OffsetDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class Phase1ScoresBulkUpdateService2 @Inject()(appRepo: GeneralApplicationRepository,
                                              phase1TestRepo: Phase1TestRepository)(implicit ec: ExecutionContext) {

  def updatePhase1Scores(scoreRequests: Seq[Phase1ScoreUpdateRequest]): Future[Seq[Phase1ScoreUpdateResponse]] = {
    Future.sequence(scoreRequests.map { scoreRequest =>
      // The FC here processes the Futures. These do not fail fast
      for {
        tryA <- checkApplicationFound(scoreRequest)
        tryB <- verifyInventoryAndOrderIds(scoreRequest)
        phase1TestProfileOpt <- phase1TestRepo.getTestGroup(scoreRequest.applicationId)
        tryC <- updateTests(scoreRequest, phase1TestProfileOpt)
      } yield {
        // The FC here processes the Trys and this is where the fail fast happens
        (for {
          a <- tryA
          b <- tryB
          c <- tryC
        } yield {
          Phase1ScoreUpdateResponse(scoreRequest, status = "Successfully updated")
        }).recover {
          case e @ (_: ApplicationNotFoundException | _: NoTestFoundException | _: IllegalStateException) =>
            Phase1ScoreUpdateResponse(scoreRequest, status = e.getMessage)
        }.get
      }
    })
  }

  private def checkApplicationFound(scoreRequest: Phase1ScoreUpdateRequest): Future[Try[Boolean]] = {
    for {
      candidateOpt <- appRepo.find(scoreRequest.applicationId)
    } yield {
      candidateOpt match {
        case Some(_) => Success(true)
        case _ => Failure(ApplicationNotFoundException(s"No application found for ${scoreRequest.applicationId}"))
      }
    }
  }

  private def verifyInventoryAndOrderIds(scoreRequest: Phase1ScoreUpdateRequest): Future[Try[Boolean]] = {
    for {
      phase1TestProfileOpt <- phase1TestRepo.getTestGroup(scoreRequest.applicationId)
    } yield {
      val idsVerified = phase1TestProfileOpt.exists { phase1TestProfile =>
        // Iterate the collection of active tests and check each one for inventoryId and orderId and presence of testResult
        phase1TestProfile.activeTests.exists { test =>
          test.inventoryId == scoreRequest.inventoryId && test.orderId == scoreRequest.orderId &&
            test.testResult.isDefined
        }
      }
      if (idsVerified) {
        Success(true)
      } else {
        Failure(NoTestFoundException(s"No test found for inventoryId=${scoreRequest.inventoryId} " +
          s"and orderId=${scoreRequest.orderId} or missing test result")
        )
      }
    }
  }

  private def updateTests(scoreRequest: Phase1ScoreUpdateRequest, phase1TestProfileOpt: Option[Phase1TestProfile]): Future[Try[Unit]] = {
    phase1TestProfileOpt match {
      case Some(phase1TestProfile) =>
        for {
          u <- phase1TestRepo.updateTestGroup(
            scoreRequest.applicationId,
            updatePhase1TestProfile(scoreRequest, phase1TestProfile)
          )
        } yield {
          Success(u)
        }

      case _ => Future.successful(Failure(new IllegalStateException("Failed to find Phase1TestProfile")))
    }
  }

  private def updatePhase1TestProfile(scoreRequest: Phase1ScoreUpdateRequest, phase1TestProfile: Phase1TestProfile): Phase1TestProfile =
    phase1TestProfile.copy(tests = updateTest(scoreRequest, phase1TestProfile.tests))

  private def updateTest(scoreRequest: Phase1ScoreUpdateRequest, tests: List[PsiTest]): List[PsiTest] = {
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

object Phase1ScoresBulkUpdateService2 {
  case class NoTestFoundException(msg: String) extends Exception(msg)
  case class ApplicationNotFoundException(msg: String) extends Exception(msg)
}
