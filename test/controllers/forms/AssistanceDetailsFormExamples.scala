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

package controllers.forms

import forms.AssistanceDetailsForm
import models.ApplicationRoute

object AssistanceDetailsFormExamples {
  val DisabilityGisAndAdjustmentsForm = AssistanceDetailsForm.Data("Yes", Some("Epilepsy"), Some("Yes"), "Yes", Some("Some adjustment"),
    Some("Yes"), Some("Some other adjustements"))

  val NoDisabilitiesForm = AssistanceDetailsForm.Data("No", None, None, "No", None, Some("No"), None)

  val NoAdjustmentsForm = AssistanceDetailsForm.Data("Yes", Some("Some disabilities"), Some("No"), "No", None, Some("No"), None)

  val FullForm = AssistanceDetailsForm.Data("Yes", Some("Some disabilities"), Some("Yes"), "Yes", Some("Some adjustments online"), Some("Yes"),
    Some("Some adjustments at venue"))

  val DisabilityGisAndAdjustmentsMap = Map[String, String](
    "applicationRoute" -> ApplicationRoute.Faststream.toString,
    "hasDisability" -> "Yes",
    "hasDisabilityDescription" -> "Epilepsy",
    "guaranteedInterview" -> "Yes",
    "needsSupportForOnlineAssessment" -> "Yes",
    "needsSupportForOnlineAssessmentDescription" -> "Some adjustment",
    "needsSupportAtVenue" -> "Yes",
    "needsSupportAtVenueDescription" -> "Some other adjustements")

  val DisabilityGisAndAdjustmentsFormUrlEncodedBody = Seq(
    "applicationRoute" -> ApplicationRoute.Faststream.toString,
    "hasDisability" -> "Yes",
    "hasDisabilityDescription" -> "Epilepsy",
    "guaranteedInterview" -> "Yes",
    "needsSupportForOnlineAssessment" -> "Yes",
    "needsSupportForOnlineAssessmentDescription" -> "Some adjustment",
    "needsSupportAtVenue" -> "Yes",
    "needsSupportAtVenueDescription" -> "Some other adjustements")
}
