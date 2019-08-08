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

import model.persisted.AssistanceDetails
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import model.{Adjustments, ApplicationRoute, ApplicationStatus}
import play.api.mvc.RequestHeader
import repositories._
import repositories.assistancedetails.AssistanceDetailsRepository
import services.adjustmentsmanagement.AdjustmentsManagementService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object InProgressAssistanceDetailsStatusGenerator extends InProgressAssistanceDetailsStatusGenerator {
  val previousStatusGenerator = InProgressSchemePreferencesStatusGenerator
  val adRepository = faststreamAssistanceDetailsRepository
  val adjustmentsManagementService = AdjustmentsManagementService
}

// scalastyle:off method.length
trait InProgressAssistanceDetailsStatusGenerator extends ConstructiveGenerator {
  val adRepository: AssistanceDetailsRepository
  val adjustmentsManagementService: AdjustmentsManagementService

  override def getPreviousStatusGenerator(generatorConfig: CreateCandidateData) = {
    (ApplicationStatus.IN_PROGRESS, InProgressSchemePreferencesStatusGenerator)
  }

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    val assistanceDetails = getAssistanceDetails(generatorConfig)
    val adjustmentsDataOpt = generatorConfig.adjustmentInformation

    for {
      candidateInPreviousStatus <- getPreviousStatusGenerator(generatorConfig)._2.generate(generationId, generatorConfig)
      appId = candidateInPreviousStatus.applicationId.get
      _ <- adRepository.update(appId, candidateInPreviousStatus.userId, assistanceDetails)
      _ <- if (adjustmentsDataOpt.exists(_.adjustmentsConfirmed.getOrElse(false))) {
        adjustmentsManagementService.confirmAdjustment(appId, adjustmentsDataOpt.get)
      } else {
        Future.successful(())
      }
    } yield {
      candidateInPreviousStatus.copy(assistanceDetails = Some(assistanceDetails),
        adjustmentInformation = adjustmentsDataOpt)
    }
  }

  private def getAssistanceDetails(config: CreateCandidateData): AssistanceDetails = {
    val hasDisabilityFinalValue = config.assistanceDetails.hasDisability

    val hasDisabilityDescriptionFinalValue =
      if (hasDisabilityFinalValue == "Yes") {
        Some(config.assistanceDetails.hasDisabilityDescription)
      } else {
        None
      }
    val gisFinalValue = if (hasDisabilityFinalValue == "Yes" && config.assistanceDetails.setGis) {
      Some(true)
    } else {
      Some(false)
    }

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
    val phoneAdjustmentsFinalValue = if (config.statusData.applicationRoute == ApplicationRoute.Edip ||
      config.statusData.applicationRoute == ApplicationRoute.Sdip) {
      config.assistanceDetails.phoneAdjustments
    } else {
      false
    }
    val phoneAdjustmentsDescriptionFinalValue =
      if (phoneAdjustmentsFinalValue) {
        Some(config.assistanceDetails.phoneAdjustmentsDescription)
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
      Some(phoneAdjustmentsFinalValue),
      phoneAdjustmentsDescriptionFinalValue
    )
  }
}
