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

package services.testdata.onlinetests

import connectors.testdata.ExchangeObjects
import model.ProgressStatuses._
import model.command.testdata.GeneratorConfig
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import services.testdata.ConstructiveGenerator
import services.testdata.onlinetests.phase1.Phase1TestsResultsReceivedStatusGenerator
import services.testdata.onlinetests.phase2.Phase2TestsResultsReceivedStatusGenerator
import services.testdata.onlinetests.phase3.Phase3TestsResultsReceivedStatusGenerator
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Phase1TestsFailedStatusGenerator extends TestsFailedStatusGenerator {
  val previousStatusGenerator = Phase1TestsResultsReceivedStatusGenerator
  val appRepository = applicationRepository
  val failedStatus = PHASE1_TESTS_FAILED
}

object Phase2TestsFailedStatusGenerator extends TestsFailedStatusGenerator {
  val previousStatusGenerator = Phase2TestsResultsReceivedStatusGenerator
  val appRepository = applicationRepository
  val failedStatus = PHASE2_TESTS_FAILED
}

object Phase3TestsFailedStatusGenerator extends TestsFailedStatusGenerator {
  val previousStatusGenerator = Phase3TestsResultsReceivedStatusGenerator
  val appRepository = applicationRepository
  val failedStatus = PHASE3_TESTS_FAILED
}

trait TestsFailedStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val failedStatus: ProgressStatus

  def generate(generationId: Int,
               generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier,
                                                 rh: RequestHeader): Future[ExchangeObjects.DataGenerationResponse] = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(candidate.applicationId.get, failedStatus)
    } yield candidate
  }
}
