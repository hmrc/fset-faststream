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

package forms

import connectors.exchange.AssistanceDetails
import play.api.data.Form
import play.api.data.Forms._

object AssistanceDetailsForm {

  val form = Form(
    mapping(
      "hasDisability" -> Mappings.nonEmptyTrimmedText("error.hasDisability.required", 31),
      "hasDisabilityDescription" -> optional(Mappings.nonEmptyTrimmedText("error.hasDisabilityDescription.required", 2048)),
      "guaranteedInterview" -> of(requiredFormatterWithMaxLengthCheck("hasDisability", "guaranteedInterview", None)),
      "needsSupportForOnlineAssessment" -> Mappings.nonEmptyTrimmedText("error.needsSupportForOnlineAssessment.required", 31),
      "needsSupportForOnlineAssessmentDescription" -> of(requiredFormatterWithMaxLengthCheck("needsSupportForOnlineAssessment",
        "needsSupportForOnlineAssessmentDescription", Some(2048))),
      "needsSupportAtVenue" -> Mappings.nonEmptyTrimmedText("error.needsSupportAtVenue.required", 31),
      "needsSupportAtVenueDescription" -> of(requiredFormatterWithMaxLengthCheck("needsSupportAtVenue", "needsSupportAtVenueDescription",
        Some(2048)))
    )(Data.apply)(Data.unapply)
  )

  case class Data(
                   hasDisability: String,
                   hasDisabilityDescription: Option[String],
                   guaranteedInterview: Option[String],
                   needsSupportForOnlineAssessment: String,
                   needsSupportForOnlineAssessmentDescription: Option[String],
                   needsSupportAtVenue: String,
                   needsSupportAtVenueDescription: Option[String]) {

    def exchange: AssistanceDetails = {
      AssistanceDetails(
        hasDisability,
        hasDisabilityDescription,
        guaranteedInterview.map {
          case "Yes" => true
          case "No" => false
          case _ => false
        },
        needsSupportForOnlineAssessment match {
          case "Yes" => true
          case "No" => false
          case _ => false
        },
        needsSupportForOnlineAssessmentDescription,
        needsSupportAtVenue match {
          case "Yes" => true
          case "No" => false
          case _ => false
        },
        needsSupportAtVenueDescription
      )
    }

    def sanitizeData: AssistanceDetailsForm.Data = {
      AssistanceDetailsForm.Data(
        hasDisability,
        if (hasDisability == "Yes") hasDisabilityDescription else None,
        if (hasDisability == "Yes") guaranteedInterview else None,
        needsSupportForOnlineAssessment,
        if (needsSupportForOnlineAssessment == "Yes") needsSupportForOnlineAssessmentDescription else None,
        needsSupportAtVenue,
        if (needsSupportAtVenue == "Yes") needsSupportAtVenueDescription else None)
    }
  }

  object Data{
    def apply(ad: AssistanceDetails): Data = {
      AssistanceDetailsForm.Data(
        ad.hasDisability,
        ad.hasDisabilityDescription,
        ad.guaranteedInterview.map {
          case true => "Yes"
          case false => "No"
        },
        ad.needsSupportForOnlineAssessment match {
          case true => "Yes"
          case false => "No"
        },
        ad.needsSupportForOnlineAssessmentDescription,
        ad.needsSupportAtVenue match {
          case true => "Yes"
          case false => "No"
        },
        ad.needsSupportAtVenueDescription
      )
    }


  }
}
