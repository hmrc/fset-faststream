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

package services.testdata.candidate.fsb

import javax.inject.{Inject, Provider, Singleton}
import model.ApplicationStatus.ApplicationStatus
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import model.{ApplicationRoute, ApplicationStatus, ProgressStatuses}
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import services.testdata.candidate.assessmentcentre._
import services.testdata.candidate.sift.SiftCompleteStatusGenerator
import services.testdata.candidate.{BaseGenerator, CandidateStatusGeneratorFactory, ConstructiveGenerator}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

//object FsbAwaitingAllocationStatusGenerator extends FsbAwaitingAllocationStatusGenerator {
//  override val previousStatusGenerator: BaseGenerator = AssessmentCentrePassedStatusGenerator
//  override val applicationRepository = repositories.applicationRepository
//}

@Singleton
class FsbAwaitingAllocationStatusGenerator @Inject() (val previousStatusGenerator: AssessmentCentrePassedStatusGenerator,
                                                      candidateStatusGeneratorFactory: Provider[CandidateStatusGeneratorFactory],
                                                      siftCompleteStatusGenerator: SiftCompleteStatusGenerator,
                                                      applicationRepository: GeneralApplicationRepository,
                                                      assessmentCentrePassedStatusGenerator: AssessmentCentrePassedStatusGenerator
                                                     )(implicit ec: ExecutionContext) extends ConstructiveGenerator {

  override def getPreviousStatusGenerator(generatorConfig: CreateCandidateData): (ApplicationStatus, BaseGenerator) = {
    val previousStatusAndGeneratorPair = generatorConfig.statusData.previousApplicationStatus.map(previousApplicationStatus => {
      val generator = candidateStatusGeneratorFactory.get().getGenerator(
        generatorConfig.copy(
          statusData = generatorConfig.statusData.copy(
            applicationStatus = previousApplicationStatus
          )))
      (previousApplicationStatus, generator)
    })

    // TODO: SdipFaststream
    previousStatusAndGeneratorPair.getOrElse(
      if (List(ApplicationRoute.Sdip, ApplicationRoute.Edip).contains(generatorConfig.statusData.applicationRoute)) {
        (ApplicationStatus.SIFT, siftCompleteStatusGenerator)
      } else {
        (ApplicationStatus.ASSESSMENT_CENTRE, assessmentCentrePassedStatusGenerator)
      }
    )
  }

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[CreateCandidateResponse] = {

    for {
      (_, previousStatusGenerator) <- Future.successful(getPreviousStatusGenerator(generatorConfig))
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
