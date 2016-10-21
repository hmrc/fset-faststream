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
import services.testdata.faker.DataFaker._

object InProgressAssistanceDetailsStatusGenerator extends InProgressAssistanceDetailsStatusGenerator {
  override val previousStatusGenerator = InProgressPartnerGraduateProgrammesStatusGenerator
  override val adRepository = faststreamAssistanceDetailsRepository
}

trait InProgressAssistanceDetailsStatusGenerator extends ConstructiveGenerator {
  val adRepository: AssistanceDetailsRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    def getAssistanceDetails(generatorConfig: GeneratorConfig) = {
      import generatorConfig._

      val hasDisabilityFinalValue = hasDisability.getOrElse(Random.yesNoPreferNotToSay)
      val hasDisabilityDescriptionFinalValue = if (hasDisabilityFinalValue == "Yes") Some(Random.hasDisabilityDescription) else None
      val gisFinalValue = if (hasDisabilityFinalValue== "Yes" && setGis) Some(true) else Some(false)
      val onlineAdjustmentsFinalValue = onlineAdjustments.getOrElse(Random.bool)
      val onlineAdjustmentsDescriptionFinalValue = if (onlineAdjustmentsFinalValue) {
        Some(onlineAdjustmentsDescription.getOrElse(Random.onlineAdjustmentsDescription))
      } else {
        None
      }
      val assessmentCentreAdjustmentsFinalValue = assessmentCentreAdjustments.getOrElse(Random.bool)
      val assessmentCentreAdjustmentsDescriptionFinalValue = if (assessmentCentreAdjustmentsFinalValue) {
        Some(assessmentCentreAdjustmentsDescription.getOrElse(Random.assessmentCentreAdjustmentDescription))
      } else {
        None
      }
      AssistanceDetails(
        hasDisabilityFinalValue,
        hasDisabilityDescriptionFinalValue,
        gisFinalValue,
        onlineAdjustmentsFinalValue,
        onlineAdjustmentsDescriptionFinalValue,
        assessmentCentreAdjustmentsFinalValue,
        assessmentCentreAdjustmentsDescriptionFinalValue)
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- adRepository.update(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId,
        getAssistanceDetails(generatorConfig))
    } yield {
      candidateInPreviousStatus
    }
  }
}
