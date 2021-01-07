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

package services.testdata.candidate.onlinetests

import javax.inject.{ Inject, Singleton }
import model.ProgressStatuses._
import model.exchange.testdata.CreateCandidateResponse
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.candidate.assessmentcentre.AssessmentCentreAllocationConfirmedStatusGenerator
import services.testdata.candidate.fsb.FsbResultEnteredStatusGenerator
import services.testdata.candidate.onlinetests.phase1.Phase1TestsResultsReceivedStatusGenerator
import services.testdata.candidate.onlinetests.phase2.Phase2TestsResultsReceivedStatusGenerator
import services.testdata.candidate.onlinetests.phase3.Phase3TestsResultsReceivedStatusGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TestsFailedStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val failedStatus: ProgressStatus

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse.CreateCandidateResponse] = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(candidate.applicationId.get, failedStatus)
    } yield candidate
  }
}

@Singleton
class Phase1TestsFailedStatusGenerator @Inject() (val previousStatusGenerator: Phase1TestsResultsReceivedStatusGenerator,
                                                  val appRepository: GeneralApplicationRepository
                                                 ) extends TestsFailedStatusGenerator {
  val failedStatus = PHASE1_TESTS_FAILED
}

@Singleton
class Phase2TestsFailedStatusGenerator @Inject() (val previousStatusGenerator: Phase2TestsResultsReceivedStatusGenerator,
                                                  val appRepository: GeneralApplicationRepository
                                                 ) extends TestsFailedStatusGenerator {
  val failedStatus = PHASE2_TESTS_FAILED
}

@Singleton
class Phase3TestsFailedStatusGenerator @Inject() (val previousStatusGenerator: Phase3TestsResultsReceivedStatusGenerator,
                                                  val appRepository: GeneralApplicationRepository
                                                 ) extends TestsFailedStatusGenerator {
  val failedStatus = PHASE3_TESTS_FAILED
}

@Singleton
class AssessmentCentreFailedStatusGenerator @Inject() (val previousStatusGenerator: AssessmentCentreAllocationConfirmedStatusGenerator,
                                                       val appRepository: GeneralApplicationRepository
                                                      ) extends TestsFailedStatusGenerator {
  val failedStatus = ASSESSMENT_CENTRE_FAILED
}

@Singleton
class FsbFailedStatusGenerator @Inject() (val previousStatusGenerator: FsbResultEnteredStatusGenerator,
                                          val appRepository: GeneralApplicationRepository
                                         ) extends TestsFailedStatusGenerator {
  val failedStatus = FSB_FAILED
}

@Singleton
class AllFsbFailedStatusGenerator @Inject() (val previousStatusGenerator: FsbResultEnteredStatusGenerator,
                                             val appRepository: GeneralApplicationRepository
                                            ) extends TestsFailedStatusGenerator {
  val failedStatus = ALL_FSBS_AND_FSACS_FAILED
}
