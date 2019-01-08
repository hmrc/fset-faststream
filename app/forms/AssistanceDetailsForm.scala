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

package forms

import connectors.exchange.AssistanceDetails
import models.ApplicationRoute._
import play.api.data.Form
import play.api.data.Forms._

object AssistanceDetailsForm {

  val isFastStream: (Map[String, String]) => Boolean = (requestParams: Map[String, String]) =>
    requestParams.getOrElse("applicationRoute", Faststream.toString) == Faststream.toString

  val isSdipFastStream: (Map[String, String]) => Boolean = (requestParams: Map[String, String]) =>
    requestParams.getOrElse("applicationRoute", SdipFaststream.toString) == SdipFaststream.toString

  val isFastStreamOrSdipFastStream: (Map[String, String]) => Boolean = (requestParams: Map[String, String]) =>
    isFastStream(requestParams) || isSdipFastStream(requestParams)

  val isEdipOrSdip: (Map[String, String]) => Boolean = (requestParams: Map[String, String]) => {
    requestParams.getOrElse("applicationRoute", Faststream.toString) == Edip.toString ||
      requestParams.getOrElse("applicationRoute", Faststream.toString) == Sdip.toString
  }

  val form = Form(
    mapping(
      "hasDisability" -> Mappings.nonEmptyTrimmedText("error.hasDisability.required", 31),
      "hasDisabilityDescription" -> optional(Mappings.nonEmptyTrimmedText("error.hasDisabilityDescription.required", 2048)),
      "guaranteedInterview" -> of(requiredFormatterWithMaxLengthCheck("hasDisability", "guaranteedInterview", None)),
      "needsSupportForOnlineAssessment" -> of(
        Mappings.mayBeOptionalString("error.needsSupportForOnlineAssessment.required", 31, isFastStreamOrSdipFastStream)),
      "needsSupportForOnlineAssessmentDescription" -> of(requiredFormatterWithMaxLengthCheck("needsSupportForOnlineAssessment",
        "needsSupportForOnlineAssessmentDescription", Some(2048))),
      "needsSupportAtVenue" -> of(Mappings.mayBeOptionalString("error.needsSupportAtVenue.required", 31, isFastStreamOrSdipFastStream)),
      "needsSupportAtVenueDescription" -> of(requiredFormatterWithMaxLengthCheck("needsSupportAtVenue", "needsSupportAtVenueDescription",
        Some(2048))),
      "needsSupportForPhoneInterview" -> of(Mappings.mayBeOptionalString("error.needsSupportForPhoneInterview.required", 31, isEdipOrSdip)),
      "needsSupportForPhoneInterviewDescription" ->
        of(requiredFormatterWithMaxLengthCheck("needsSupportForPhoneInterview", "needsSupportForPhoneInterviewDescription", Some(2048)))
    )(Data.apply)(Data.unapply)
  )

  import Data._
  case class Data(
                   hasDisability: String,
                   hasDisabilityDescription: Option[String],
                   guaranteedInterview: Option[String],
                   needsSupportForOnlineAssessment: Option[String],
                   needsSupportForOnlineAssessmentDescription: Option[String],
                   needsSupportAtVenue: Option[String],
                   needsSupportAtVenueDescription: Option[String],
                   needsSupportForPhoneInterview: Option[String],
                   needsSupportForPhoneInterviewDescription: Option[String]) {

    def exchange: AssistanceDetails = {
      AssistanceDetails(
        hasDisability,
        hasDisabilityDescription,
        guaranteedInterview.map {
          case "Yes" => true
          case "No" => false
          case _ => false
        },
        toOptBoolean(needsSupportForOnlineAssessment),
        needsSupportForOnlineAssessmentDescription,
        toOptBoolean(needsSupportAtVenue),
        needsSupportAtVenueDescription,
        toOptBoolean(needsSupportForPhoneInterview),
        needsSupportForPhoneInterviewDescription
      )
    }

    def sanitizeData: AssistanceDetailsForm.Data = {
      AssistanceDetailsForm.Data(
        hasDisability,
        if (hasDisability == "Yes") hasDisabilityDescription else None,
        if (hasDisability == "Yes") guaranteedInterview else None,
        needsSupportForOnlineAssessment,
        if (needsSupportForOnlineAssessment.contains("Yes")) needsSupportForOnlineAssessmentDescription else None,
        needsSupportAtVenue,
        if (needsSupportAtVenue.contains("Yes")) needsSupportAtVenueDescription else None,
        needsSupportForPhoneInterview,
        if (needsSupportForPhoneInterview.contains("Yes")) needsSupportForPhoneInterviewDescription else None
      )
    }
  }

  object Data {
    def apply(ad: AssistanceDetails): Data = {
      AssistanceDetailsForm.Data(
        ad.hasDisability,
        ad.hasDisabilityDescription,
        ad.guaranteedInterview.map {
          case true => "Yes"
          case false => "No"
        },
        toOptString(ad.needsSupportForOnlineAssessment),
        ad.needsSupportForOnlineAssessmentDescription,
        toOptString(ad.needsSupportAtVenue),
        ad.needsSupportAtVenueDescription,
        toOptString(ad.needsSupportForPhoneInterview),
        ad.needsSupportForPhoneInterviewDescription
      )
    }

    def toOptBoolean(optString: Option[String]) = optString match {
      case Some("Yes") => Some(true)
      case Some("No") => Some(false)
      case _ => None
    }

    def toOptString(optBoolean: Option[Boolean]) = optBoolean match {
      case Some(true) => Some("Yes")
      case Some(false) => Some("No")
      case _ => None
    }
  }
}
