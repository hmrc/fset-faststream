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

package connectors.exchange

import forms.AssistanceDetailsForm

object AssistanceDetailsExamples {
  val OnlyDisabilityNoGisNoAdjustments = AssistanceDetails(hasDisability = "Yes", disabilityImpact = Some("No"),
    disabilityCategories = Some(List(AssistanceDetailsForm.disabilityCategoriesList.head)),
    otherDisabilityDescription = None, guaranteedInterview = Some(false), needsSupportForOnlineAssessment = Some(false),
    needsSupportForOnlineAssessmentDescription = None, needsSupportAtVenue = Some(false),
    needsSupportAtVenueDescription = None, needsSupportForPhoneInterview = None, needsSupportForPhoneInterviewDescription = None)
  val DisabilityGisAndAdjustments = AssistanceDetails(hasDisability = "Yes", disabilityImpact = Some("No"),
    disabilityCategories = Some(List(AssistanceDetailsForm.disabilityCategoriesList.head)),
    otherDisabilityDescription = None, guaranteedInterview = Some(true), needsSupportForOnlineAssessment = Some(true),
    needsSupportForOnlineAssessmentDescription = Some("Some adjustment"), needsSupportAtVenue = Some(true),
    needsSupportAtVenueDescription = Some("Some other adjustments"), needsSupportForPhoneInterview = None,
    needsSupportForPhoneInterviewDescription = None)
  val EdipAdjustments = AssistanceDetails(hasDisability = "Yes", disabilityImpact = Some("No"),
    disabilityCategories = Some(List(AssistanceDetailsForm.disabilityCategoriesList.head)), otherDisabilityDescription = None,
    guaranteedInterview = Some(true), needsSupportForOnlineAssessment = None, needsSupportForOnlineAssessmentDescription = None,
    needsSupportAtVenue = None, needsSupportAtVenueDescription = None, needsSupportForPhoneInterview = Some(true),
    needsSupportForPhoneInterviewDescription = Some("Some adjustment"))
  val SdipAdjustments = AssistanceDetails(hasDisability = "Yes", disabilityImpact = Some("No"),
    disabilityCategories = Some(List(AssistanceDetailsForm.disabilityCategoriesList.head)), otherDisabilityDescription = None,
    guaranteedInterview = Some(true), needsSupportForOnlineAssessment = None, needsSupportForOnlineAssessmentDescription = None,
    needsSupportAtVenue = None, needsSupportAtVenueDescription = None, needsSupportForPhoneInterview = Some(true),
    needsSupportForPhoneInterviewDescription = Some("Some adjustment"))
}
