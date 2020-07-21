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

package connectors.exchange

import play.api.libs.json.Json

final case class AssistanceDetails(
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
) {
  def requiresAdjustments: Boolean = {
    List(
      guaranteedInterview.contains(true),
      needsSupportForOnlineAssessment.contains(true),
      needsSupportAtVenue.contains(true),
      needsSupportForPhoneInterview.contains(true)
    ).contains(true)
  }

  def isDisabledCandidate = hasDisability.toLowerCase == "yes"
  def hasSelectedOtherDisabilityCategory=
    disabilityCategories.exists ( disabilityCategoryList => disabilityCategoryList.contains("Other") )
}

object AssistanceDetails {
  implicit val assistanceDetailsFormat = Json.format[AssistanceDetails]
}
