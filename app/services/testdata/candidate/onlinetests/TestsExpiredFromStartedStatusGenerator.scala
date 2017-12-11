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

package services.testdata.candidate.onlinetests

import model.ProgressStatuses.{ PHASE1_TESTS_EXPIRED, PHASE2_TESTS_EXPIRED, PHASE3_TESTS_EXPIRED, ProgressStatus }
import model.testdata.CreateCandidateData
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.OnlineTestRepository
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.Phase1TestService
import services.onlinetesting.phase2.Phase2TestService
import services.onlinetesting.phase3.Phase3TestService
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.candidate.onlinetests.phase1.Phase1TestsStartedStatusGenerator
import services.testdata.candidate.onlinetests.phase2.Phase2TestsStartedStatusGenerator
import services.testdata.candidate.onlinetests.phase3.Phase3TestsStartedStatusGenerator

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object Phase1TestsExpiredFromStartedStatusGenerator extends TestsExpiredFromStartedStatusGenerator {
  override val previousStatusGenerator = Phase1TestsStartedStatusGenerator
  override val otRepository = phase1TestRepository
  override val otService = Phase1TestService
  override val expiredStatus = PHASE1_TESTS_EXPIRED
}

object Phase2TestsExpiredFromStartedStatusGenerator extends TestsExpiredFromStartedStatusGenerator {
  override val previousStatusGenerator = Phase2TestsStartedStatusGenerator
  override val otRepository = phase2TestRepository
  override val otService = Phase2TestService
  override val expiredStatus = PHASE2_TESTS_EXPIRED
}

object Phase3TestsExpiredFromStartedStatusGenerator extends TestsExpiredFromStartedStatusGenerator {
  override val previousStatusGenerator = Phase3TestsStartedStatusGenerator
  override val otRepository = phase3TestRepository
  override val otService = Phase3TestService
  override val expiredStatus = PHASE3_TESTS_EXPIRED
}

trait TestsExpiredFromStartedStatusGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository
  val otService: OnlineTestService
  val expiredStatus: ProgressStatus

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otService.commitProgressStatus(candidateInPreviousStatus.applicationId.get, expiredStatus)
    } yield {
      candidateInPreviousStatus
    }

  }
}
