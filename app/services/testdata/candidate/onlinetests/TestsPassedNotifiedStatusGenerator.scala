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

package services.testdata.candidate.onlinetests

import javax.inject.{Inject, Singleton}
import model.ProgressStatuses.{PHASE1_TESTS_PASSED_NOTIFIED, PHASE3_TESTS_PASSED_NOTIFIED, ProgressStatus}
import model.command.ApplicationForSkippingPhase3
import model.exchange.testdata.CreateCandidateResponse.{CreateCandidateResponse, TestGroupResponse}
import model.persisted.{PassmarkEvaluation, SchemeEvaluationResult}
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase3TestRepository
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Phase1TestsPassedNotifiedStatusGenerator @Inject() (val previousStatusGenerator: Phase1TestsPassedStatusGenerator,
                                                          val appRepository: GeneralApplicationRepository
                                                        )(implicit ec: ExecutionContext) extends TestsPassedNotifiedStatusGenerator {
  val notifiedStatus = PHASE1_TESTS_PASSED_NOTIFIED
}

@Singleton
class Phase3TestsPassedNotifiedStatusGenerator @Inject() (val previousStatusGenerator: Phase2TestsPassedStatusGenerator,
                                                          val appRepository: GeneralApplicationRepository,
                                                          val phase3TestRepository: Phase3TestRepository
                                                         )(implicit ec: ExecutionContext) extends ConstructiveGenerator {
  override def generate(generationId: Int,
                        generatorConfig:
                        CreateCandidateData)(
    implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[CreateCandidateResponse] = {

    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      css <- appRepository.getCurrentSchemeStatus(candidate.applicationId.get)
      _ <- phase3TestRepository.skipPhase3(ApplicationForSkippingPhase3(candidate.applicationId.get, css))
    } yield {
      updateGenerationResponse(candidate, css)
    }
  }

  // Update the CreateCandidateResponse object with the data the upstream generator needs
  def updateGenerationResponse(ccr: CreateCandidateResponse, css: Seq[SchemeEvaluationResult]): CreateCandidateResponse = {
    ccr.copy(
      phase3TestGroup = Some(
        TestGroupResponse(
          tests = Nil,
          schemeResult = Some(PassmarkEvaluation(
            passmarkVersion = "TestVersion",
            previousPhasePassMarkVersion = None,
            result = css.toList,
            resultVersion = "TestResultVersion",
            previousPhaseResultVersion = None))
        )
      )
    )
  }
}

trait TestsPassedNotifiedStatusGenerator extends ConstructiveGenerator {
  def appRepository: GeneralApplicationRepository
  def notifiedStatus: ProgressStatus

  override def generate(generationId: Int,
                        generatorConfig:
                        CreateCandidateData)(
    implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[CreateCandidateResponse] = {

    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(candidate.applicationId.get, notifiedStatus)
    } yield {
      candidate
    }
  }
}
