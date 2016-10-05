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

import model.persisted.AssistanceDetails
import play.api.mvc.RequestHeader
import repositories._
import repositories.assistancedetails.AssistanceDetailsRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object InProgressAssistanceDetailsStatusGenerator extends InProgressAssistanceDetailsStatusGenerator {
  override val previousStatusGenerator = InProgressPartnerGraduateProgrammesStatusGenerator
  override val adRepository = faststreamAssistanceDetailsRepository
}

trait InProgressAssistanceDetailsStatusGenerator extends ConstructiveGenerator {
  val adRepository: AssistanceDetailsRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    def getAssistanceDetails(gis: Boolean) = {
      if (gis) {
        AssistanceDetails("Yes", Some("disability"), Some(true), true, Some("adjustment online"), true, Some("adjustment at venue"))
      } else {
        AssistanceDetails("Yes", Some("disability"), Some(false), true, Some("adjustment online"), true, Some("adjustment at venue"))
      }
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- adRepository.update(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId,
        getAssistanceDetails(generatorConfig.setGis))
    } yield {
      candidateInPreviousStatus
    }
  }
}
