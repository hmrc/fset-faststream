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

package model.exchange

import model.persisted.AssistanceDetails
import play.api.libs.json.{Json, OFormat}

case class AssistanceDetailsExchange(hasDisability: String,
                                     disabilityImpact: Option[String],
                                     disabilityCategories: Option[List[String]],
                                     otherDisabilityDescription: Option[String],
                                     guaranteedInterview: Option[Boolean],
                                     needsSupportAtVenue: Option[Boolean],
                                     needsSupportAtVenueDescription: Option[String],
                                     needsSupportForPhoneInterview: Option[Boolean],
                                     needsSupportForPhoneInterviewDescription: Option[String])

object AssistanceDetailsExchange {
  implicit val assistanceDetailsExchangeFormat: OFormat[AssistanceDetailsExchange] = Json.format[AssistanceDetailsExchange]

  def apply(ad: AssistanceDetails): AssistanceDetailsExchange =
    AssistanceDetailsExchange(ad.hasDisability, ad.disabilityImpact, ad.disabilityCategories,
      ad.otherDisabilityDescription, ad.guaranteedInterview,
      ad.needsSupportAtVenue, ad.needsSupportAtVenueDescription, ad.needsSupportForPhoneInterview,
      ad.needsSupportForPhoneInterviewDescription)
}
