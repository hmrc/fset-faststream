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

package services.testdata.candidate.fsb

import model.ProgressStatuses
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import services.testdata.candidate.assessmentcentre._
import services.testdata.candidate.{ BaseGenerator, ConstructiveGenerator }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object FsbAwaitingAllocationStatusGenerator extends FsbAwaitingAllocationStatusGenerator {
  override val previousStatusGenerator: BaseGenerator = AssessmentCentrePassedStatusGenerator
  override val applicationRepository = repositories.applicationRepository
}

trait FsbAwaitingAllocationStatusGenerator extends ConstructiveGenerator {
  val applicationRepository: GeneralApplicationRepository

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- applicationRepository.addProgressStatusAndUpdateAppStatus(
        candidateInPreviousStatus.applicationId.get,
        ProgressStatuses.FSB_AWAITING_ALLOCATION
      )
    } yield {
      candidateInPreviousStatus
    }
  }
}
