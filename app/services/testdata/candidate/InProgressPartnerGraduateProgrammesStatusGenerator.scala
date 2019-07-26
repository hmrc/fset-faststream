/*
 * Copyright 2019 HM Revenue & Customs
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

import model.ApplicationRoute
import model.persisted.PartnerGraduateProgrammes
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories._
import repositories.partnergraduateprogrammes.PartnerGraduateProgrammesRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object InProgressPartnerGraduateProgrammesStatusGenerator extends InProgressPartnerGraduateProgrammesStatusGenerator {
  override val previousStatusGenerator = InProgressSchemePreferencesStatusGenerator
  override val pgpRepository = faststreamPartnerGraduateProgrammesRepository
}

trait InProgressPartnerGraduateProgrammesStatusGenerator extends ConstructiveGenerator {
  val pgpRepository: PartnerGraduateProgrammesRepository

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- generatorConfig.statusData.applicationRoute match {
        case ApplicationRoute.Faststream => pgpRepository.update(candidateInPreviousStatus.applicationId.get,
          PartnerGraduateProgrammes(interested = true, Some(List("Entrepreneur First", "Police Now"))))
        case _ => Future.successful(())
      }
    } yield {
      candidateInPreviousStatus
    }
  }
}
