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

package model.persisted

import model.exchange.AssistanceDetailsExchange
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, OFormat, __}

case class AssistanceDetails(
                              hasDisability: String,
                              disabilityImpact: Option[String],
                              disabilityCategories: Option[List[String]],
                              otherDisabilityDescription: Option[String],
                              guaranteedInterview: Option[Boolean],
                              needsSupportAtVenue: Option[Boolean],
                              needsSupportAtVenueDescription: Option[String],
                              needsSupportForPhoneInterview: Option[Boolean],
                              needsSupportForPhoneInterviewDescription: Option[String]
) {
  override def toString = s"hasDisability=$hasDisability," +
    s"disabilityImpact=$disabilityImpact," +
    s"disabilityCategories=$disabilityCategories," +
    s"otherDisabilityDescription=$otherDisabilityDescription," +
    s"guaranteedInterview=$guaranteedInterview," +
    s"needsSupportAtVenue=$needsSupportAtVenue," +
    s"needsSupportAtVenueDescription=$needsSupportAtVenueDescription," +
    s"needsSupportForPhoneInterview=$needsSupportForPhoneInterview," +
    s"needsSupportForPhoneInterviewDescription=$needsSupportForPhoneInterviewDescription"
}

object AssistanceDetails {
  implicit val assistanceDetailsFormat: OFormat[AssistanceDetails] = Json.format[AssistanceDetails]

  // Provide an explicit mongo format here to deal with the sub-document root
  // This data lives in the application collection
  val root = "assistance-details"
  val mongoFormat: Format[AssistanceDetails] = (
    (__ \ root \ "hasDisability").format[String] and
      (__ \ root \ "disabilityImpact").formatNullable[String] and
      (__ \ root \ "disabilityCategories").formatNullable[List[String]] and
      (__ \ root \ "otherDisabilityDescription").formatNullable[String] and
      (__ \ root \ "guaranteedInterview").formatNullable[Boolean] and
      (__ \ root \ "needsSupportAtVenue").formatNullable[Boolean] and
      (__ \ root \ "needsSupportAtVenueDescription").formatNullable[String] and
      (__ \ root \ "needsSupportForPhoneInterview").formatNullable[Boolean] and
      (__ \ root \ "needsSupportForPhoneInterviewDescription").formatNullable[String]
    )(AssistanceDetails.apply, unlift(AssistanceDetails.unapply))

  def apply(ex: AssistanceDetailsExchange): AssistanceDetails =
    AssistanceDetails(ex.hasDisability, ex.disabilityImpact, ex.disabilityCategories,
      ex.otherDisabilityDescription, ex.guaranteedInterview,
      ex.needsSupportAtVenue, ex.needsSupportAtVenueDescription,
      ex.needsSupportForPhoneInterview, ex.needsSupportForPhoneInterviewDescription
    )
}
