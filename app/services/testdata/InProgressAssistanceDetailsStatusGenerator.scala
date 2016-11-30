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

import model.command.testdata.GeneratorConfig
import model.persisted.AssistanceDetails
import play.api.mvc.RequestHeader
import repositories._
import repositories.assistancedetails.AssistanceDetailsRepository
import services.adjustmentsmanagement.AdjustmentsManagementService
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object InProgressAssistanceDetailsStatusGenerator extends InProgressAssistanceDetailsStatusGenerator {
  val previousStatusGenerator = InProgressPartnerGraduateProgrammesStatusGenerator
  val adRepository = faststreamAssistanceDetailsRepository
  val adjustmentsManagementService = AdjustmentsManagementService
}

// scalastyle:off method.length
trait InProgressAssistanceDetailsStatusGenerator extends ConstructiveGenerator {
  val adRepository: AssistanceDetailsRepository
  val adjustmentsManagementService: AdjustmentsManagementService

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    val assistanceDetails = getAssistanceDetails(generatorConfig)
    val maybeAdjustments = generatorConfig.adjustmentInformation

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = candidateInPreviousStatus.applicationId.get
      _ <- adRepository.update(appId, candidateInPreviousStatus.userId, assistanceDetails)
      _ <- if (maybeAdjustments.exists(_.adjustmentsConfirmed.getOrElse(false))) {
        adjustmentsManagementService.confirmAdjustment(appId, maybeAdjustments.get)
      } else {
        Future.successful()
      }
    } yield {
      candidateInPreviousStatus.copy(assistanceDetails = Some(assistanceDetails), adjustmentInformation = maybeAdjustments)
    }
  }

  private def getAssistanceDetails(config: GeneratorConfig): AssistanceDetails = {
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
}
