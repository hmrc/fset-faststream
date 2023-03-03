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

package services.testdata.candidate

import javax.inject.{Inject, Provider, Singleton}
import model.ApplicationStatus._
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

@Singleton
class WithdrawnStatusGenerator @Inject() (appRepository: GeneralApplicationRepository,
                                          candidateStatusGeneratorFactory: Provider[CandidateStatusGeneratorFactory]
                                         )(implicit ec: ExecutionContext) extends BaseGenerator {
  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext) = {

    val previousStatusGenerator = candidateStatusGeneratorFactory.get().getGenerator(
      generatorConfig.copy(
        statusData = generatorConfig.statusData.copy(
          applicationStatus = generatorConfig.statusData.previousApplicationStatus.getOrElse(SUBMITTED)
        )
      )
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository.withdraw(
        candidateInPreviousStatus.applicationId.get,
        model.command.WithdrawApplication("test", Some("test"), "Candidate")
      )
    } yield {
      candidateInPreviousStatus
    }
  }
}
