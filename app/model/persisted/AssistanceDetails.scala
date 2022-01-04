/*
 * Copyright 2022 HM Revenue & Customs
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

package model.persisted

import model.exchange.AssistanceDetailsExchange
import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class AssistanceDetails(
                              hasDisability: String,
                              disabilityImpact: Option[String],
                              disabilityCategories: Option[List[String]],
                              otherDisabilityDescription: Option[String],
                              guaranteedInterview: Option[Boolean],
                              needsSupportForOnlineAssessment: Option[Boolean],
                              needsSupportForOnlineAssessmentDescription: Option[String],
                              needsSupportAtVenue: Option[Boolean],
                              needsSupportAtVenueDescription: Option[String],
                              needsSupportForPhoneInterview: Option[Boolean],
                              needsSupportForPhoneInterviewDescription: Option[String]
)

object AssistanceDetails {
  implicit val assistanceDetailsFormat = Json.format[AssistanceDetails]
  implicit val assistanceDetailsHandler = Macros.handler[AssistanceDetails]

  def apply(ex: AssistanceDetailsExchange): AssistanceDetails =
    AssistanceDetails(ex.hasDisability, ex.disabilityImpact, ex.disabilityCategories,
      ex.otherDisabilityDescription, ex.guaranteedInterview, ex.needsSupportForOnlineAssessment,
      ex.needsSupportForOnlineAssessmentDescription, ex.needsSupportAtVenue,
      ex.needsSupportAtVenueDescription, ex.needsSupportForPhoneInterview,
      ex.needsSupportForPhoneInterviewDescription)
}
