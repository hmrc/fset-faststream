/*
 * Copyright 2016 HM Revenue & Customs
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

package services.testdata.onlinetests.phase3

import model.ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED
import model.command.testdata.GeneratorConfig
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import services.onlinetesting.Phase3TestService
import services.testdata.ConstructiveGenerator
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object Phase3TestsResultsReceivedStatusGenerator extends Phase3TestsResultsReceivedStatusGenerator {
  val previousStatusGenerator = Phase3TestsCompletedStatusGenerator
  val appRepository = applicationRepository
}

trait Phase3TestsResultsReceivedStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    for {
        candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
        _ <- appRepository.addProgressStatusAndUpdateAppStatus(candidate.applicationId.get, PHASE3_TESTS_RESULTS_RECEIVED)
      } yield candidate
    }
  }
