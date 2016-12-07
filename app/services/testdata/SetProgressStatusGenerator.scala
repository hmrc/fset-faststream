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

package services.testdata

import model.ProgressStatuses._
import model.command.testdata.GeneratorConfig
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import services.testdata.onlinetests.Phase3TestsPassedStatusGenerator
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object ReadyForExportStatusGenerator extends SetProgressStatusGenerator {
  val previousStatusGenerator = Phase3TestsPassedStatusGenerator
  val appRepository = applicationRepository
  val progressStatus = PHASE3_TESTS_SUCCESS_NOTIFIED
}

object ExportedStatusGenerator extends SetProgressStatusGenerator {
  val previousStatusGenerator = ReadyForExportStatusGenerator
  val appRepository = applicationRepository
  val progressStatus = EXPORTED
}

trait SetProgressStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val progressStatus: ProgressStatus

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(candidateInPreviousStatus.applicationId.get, progressStatus)
    } yield {
      candidateInPreviousStatus
    }
  }
}
