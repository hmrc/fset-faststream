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

package services.assistancedetails

import model.exchange.AssistanceDetailsExchange
import model.persisted.AssistanceDetails
import repositories._
import repositories.assistancedetails.AssistanceDetailsRepository
import services.AuditService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssistanceDetailsService extends AssistanceDetailsService {
  val adRepository = faststreamAssistanceDetailsRepository
}

trait AssistanceDetailsService {
  val adRepository: AssistanceDetailsRepository

  def update(applicationId: String, userId: String, updateAssistanceDetails: AssistanceDetailsExchange): Future[Unit] = {
    val assistanceDetails = AssistanceDetails(updateAssistanceDetails.hasDisability, updateAssistanceDetails.hasDisabilityDescription,
      updateAssistanceDetails.guaranteedInterview, updateAssistanceDetails.needsSupportForOnlineAssessment,
      updateAssistanceDetails.needsSupportForOnlineAssessmentDescription, updateAssistanceDetails.needsSupportAtVenue,
      updateAssistanceDetails.needsSupportAtVenueDescription, updateAssistanceDetails.needsSupportForPhoneInterview,
      updateAssistanceDetails.needsSupportForPhoneInterviewDescription)

    adRepository.update(applicationId, userId, assistanceDetails)
  }

  def find(applicationId: String, userId: String): Future[AssistanceDetailsExchange] = {
    val assistanceDetailsFut = adRepository.find(applicationId)

    for {
      ad <- assistanceDetailsFut
    } yield AssistanceDetailsExchange(ad.hasDisability, ad.hasDisabilityDescription, ad.guaranteedInterview,
      ad.needsSupportForOnlineAssessment, ad.needsSupportForOnlineAssessmentDescription,
      ad.needsSupportAtVenue, ad.needsSupportAtVenueDescription, ad.needsSupportForPhoneInterview,
      ad.needsSupportForPhoneInterviewDescription)
  }
}
