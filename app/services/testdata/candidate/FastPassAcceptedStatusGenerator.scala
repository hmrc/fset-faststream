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

import javax.inject.{ Inject, Singleton }
import model.ProgressStatuses
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import services.fastpass.FastPassService
import services.scheme.SchemePreferencesService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class FastPassAcceptedStatusGenerator @Inject() (val previousStatusGenerator: SubmittedStatusGenerator,
                                                 appRepository: GeneralApplicationRepository,
                                                 fastPassService: FastPassService,
                                                 schemePreferencesService: SchemePreferencesService,
                                                 csedRepository: CivilServiceExperienceDetailsRepository
                                                ) extends ConstructiveGenerator {

//scalastyle:off
  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository
        .addProgressStatusAndUpdateAppStatus(candidateInPreviousStatus.applicationId.get, ProgressStatuses.FAST_PASS_ACCEPTED)
      preferences <- schemePreferencesService.find(candidateInPreviousStatus.applicationId.get)
      _ <- fastPassService.createCurrentSchemeStatus(candidateInPreviousStatus.applicationId.get, preferences)
      _ <- csedRepository.evaluateFastPassCandidate(candidateInPreviousStatus.applicationId.get, accepted = true)
    } yield candidateInPreviousStatus
  }//scalastyle:on
}
