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
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

trait TestsFailedNotifiedStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val failedStatus: ProgressStatus

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(candidate.applicationId.get, failedStatus)
    } yield candidate
  }
}

@Singleton
class Phase1TestsFailedNotifiedStatusGenerator @Inject() (val previousStatusGenerator: Phase1TestsFailedStatusGenerator,
                                                          val appRepository: GeneralApplicationRepository
                                                         ) extends TestsFailedNotifiedStatusGenerator {
  val failedStatus = PHASE1_TESTS_FAILED_NOTIFIED
}

@Singleton
class Phase2TestsFailedNotifiedStatusGenerator @Inject() (val previousStatusGenerator: Phase2TestsFailedStatusGenerator,
                                                          val appRepository: GeneralApplicationRepository
                                                         ) extends TestsFailedNotifiedStatusGenerator {
  val failedStatus = PHASE2_TESTS_FAILED_NOTIFIED
}

@Singleton
class Phase3TestsFailedNotifiedStatusGenerator @Inject() (val previousStatusGenerator: Phase3TestsFailedStatusGenerator,
                                                          val appRepository: GeneralApplicationRepository
                                                         ) extends TestsFailedNotifiedStatusGenerator {
  val failedStatus = PHASE3_TESTS_FAILED_NOTIFIED
}

@Singleton
class AssessmentCentreFailedNotifiedStatusGenerator @Inject() (val previousStatusGenerator: AssessmentCentreFailedStatusGenerator,
                                                               val appRepository: GeneralApplicationRepository
                                                              ) extends TestsFailedStatusGenerator {
  val failedStatus = ASSESSMENT_CENTRE_FAILED_NOTIFIED
}

@Singleton
class AllFsbFailedNotifiedStatusGenerator @Inject() (val previousStatusGenerator: AllFsbFailedStatusGenerator,
                                                     val appRepository: GeneralApplicationRepository
                                                    ) extends TestsFailedStatusGenerator {
  val failedStatus = ALL_FSBS_AND_FSACS_FAILED_NOTIFIED
}
