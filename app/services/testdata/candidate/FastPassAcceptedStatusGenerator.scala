/*
 * Copyright 2020 HM Revenue & Customs
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

import model.ProgressStatuses
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import services.fastpass.FastPassService
import services.scheme.SchemePreferencesService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object FastPassAcceptedStatusGenerator extends FastPassAcceptedStatusGenerator {
  override val previousStatusGenerator = SubmittedStatusGenerator
  override val appRepository = applicationRepository
  override val fastPassService = FastPassService
  override val schemePreferencesService = SchemePreferencesService
  override val csedRepository = civilServiceExperienceDetailsRepository
}

trait FastPassAcceptedStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val fastPassService: FastPassService
  val schemePreferencesService: SchemePreferencesService
  val csedRepository: CivilServiceExperienceDetailsRepository


  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository
        .addProgressStatusAndUpdateAppStatus(candidateInPreviousStatus.applicationId.get, ProgressStatuses.FAST_PASS_ACCEPTED)
      preferences <- schemePreferencesService.find(candidateInPreviousStatus.applicationId.get)
      _ <- fastPassService.createCurrentSchemeStatus(candidateInPreviousStatus.applicationId.get, preferences)
      _ <- csedRepository.evaluateFastPassCandidate(candidateInPreviousStatus.applicationId.get, accepted = true)
    } yield (candidateInPreviousStatus)
  }
}
