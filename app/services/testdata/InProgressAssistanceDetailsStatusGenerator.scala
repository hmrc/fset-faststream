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
import model.command.testdata.GeneratorConfig

import scala.concurrent.ExecutionContext.Implicits.global
import services.testdata.faker.DataFaker._

object InProgressAssistanceDetailsStatusGenerator extends InProgressAssistanceDetailsStatusGenerator {
  override val previousStatusGenerator = InProgressPartnerGraduateProgrammesStatusGenerator
  override val adRepository = faststreamAssistanceDetailsRepository
}

// scalastyle:off method.length
trait InProgressAssistanceDetailsStatusGenerator extends ConstructiveGenerator {
  val adRepository: AssistanceDetailsRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    def getAssistanceDetails(config: GeneratorConfig) = {
      val hasDisabilityFinalValue = config.assistanceDetails.hasDisability

      val hasDisabilityDescriptionFinalValue =
        if (hasDisabilityFinalValue == "Yes") {
          Some(config.assistanceDetails.hasDisabilityDescription)
        } else {
          None
        }
      val gisFinalValue = if (hasDisabilityFinalValue == "Yes" && config.assistanceDetails.setGis) {
        Some(true)
      } else { Some(false) }

      val onlineAdjustmentsFinalValue = config.assistanceDetails.onlineAdjustments
      val onlineAdjustmentsDescriptionFinalValue =
        if (onlineAdjustmentsFinalValue) {
          Some(config.assistanceDetails.onlineAdjustmentsDescription)
        } else {
          None
        }
      val assessmentCentreAdjustmentsFinalValue = config.assistanceDetails.assessmentCentreAdjustments
      val assessmentCentreAdjustmentsDescriptionFinalValue =
        if (assessmentCentreAdjustmentsFinalValue) {
          Some(config.assistanceDetails.assessmentCentreAdjustmentsDescription)
        } else {
          None
        }

      val phoneInterviewAdjustmentsFinalValue = Random.bool
      val phoneInterviewAdjustmentsDescriptionFinalValue =
        if (phoneInterviewAdjustmentsFinalValue) {
          Some("")
        } else {
          None
        }

      AssistanceDetails(
        hasDisabilityFinalValue,
        hasDisabilityDescriptionFinalValue,
        gisFinalValue,
        Some(onlineAdjustmentsFinalValue),
        onlineAdjustmentsDescriptionFinalValue,
        Some(assessmentCentreAdjustmentsFinalValue),
        assessmentCentreAdjustmentsDescriptionFinalValue,
        Some(phoneInterviewAdjustmentsFinalValue),
        phoneInterviewAdjustmentsDescriptionFinalValue
      )
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
