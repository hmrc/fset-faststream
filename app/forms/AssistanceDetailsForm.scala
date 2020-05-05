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

package forms

import connectors.exchange.AssistanceDetails
import models.ApplicationRoute._
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }

import scala.language.implicitConversions

object AssistanceDetailsForm {

  val isFastStream: Map[String, String] => Boolean = (requestParams: Map[String, String]) =>
    requestParams.getOrElse("applicationRoute", Faststream.toString) == Faststream.toString

  val isSdipFastStream: Map[String, String] => Boolean = (requestParams: Map[String, String]) =>
    requestParams.getOrElse("applicationRoute", SdipFaststream.toString) == SdipFaststream.toString

  val isFastStreamOrSdipFastStream: Map[String, String] => Boolean = (requestParams: Map[String, String]) =>
    isFastStream(requestParams) || isSdipFastStream(requestParams)

  val isEdipOrSdip: Map[String, String] => Boolean = (requestParams: Map[String, String]) => {
    requestParams.getOrElse("applicationRoute", Faststream.toString) == Edip.toString ||
      requestParams.getOrElse("applicationRoute", Faststream.toString) == Sdip.toString
  }

  val preferNotToSay = "Prefer Not to Say"
  val other = "Other"
  val disabilityCategoriesList = List(
    "Deaf or Hard of Hearing",
    "Learning disability such as Down’s Syndrome & Fragile X",
    "Long-standing, chronic or fluctuating condition or disability",
    "Mental Health Condition such as depression, anxiety, bipolar, schizophrenia",
    "Neurodiverse conditions: Autism Spectrum",
    "Other neurodiverse conditions such as dyslexia, dyspraxia or AD(H)D",
    "Physical or Mobility limiting condition or disability",
    "Speech Impairment",
    "Visible Difference such as facial disfigurement, skin condition, or alopecia",
    "Visual Impairment or Sight Loss",
    preferNotToSay,
    other
  )

  val otherDisabilityCategoryMaxSize = 2048
  val form = Form(
    mapping(
      "hasDisability" -> Mappings.nonEmptyTrimmedText("error.hasDisability.required", 31),
      "disabilityImpact" -> of(disabilityImpactFormatter),
      "disabilityCategories" -> of(disabilityCategoriesFormatter),
      "otherDisabilityDescription" -> of(otherDisabilityDescriptionFormatter(otherDisabilityCategoryMaxSize)),
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

  val hasDisability = "hasDisability"
  val disabilityImpact = "disabilityImpact"
  val disabilityCategories = "disabilityCategories"
  val otherDisabilityDescription = "otherDisabilityDescription"

  private def disabilityImpactFormatter = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      bindOptionalParam(request.isHasDisabilitySelected, request.isDisabilityImpactValid,
        "You must provide a valid disability impact") (key, request.disabilityImpactParam)
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }

  def disabilityCategoriesFormatter = new Formatter[Option[List[String]]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[List[String]]] = {
      bindOptionalParam(request.isHasDisabilitySelected, request.isDisabilityCategoriesValid,
        "Choose a valid disability category")(key, request.disabilityCategoriesParam)
    }

    def unbind(key: String, value: Option[List[String]]): Map[String, String] = {
      value match {
        case Some(seq) => seq.zipWithIndex.foldLeft(Map.empty[String, String])(
          (res, pair) => res + (s"$key[${pair._2}]" -> pair._1))
        case None => Map.empty[String, String]
      }
    }
  }

  private def otherDisabilityDescriptionFormatter(maxSize: Int) = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {

      val dependencyCheck = request.isOtherDisabilitySelected
      val isFilled = request.isOtherDisabilityDescriptionFilled
      val isCorrectSize = request.isOtherDisabilityDescriptionSizeValid(maxSize)

      (dependencyCheck, isFilled, isCorrectSize) match {
        case (true, false, _) => Left(List(FormError(key, "You must provide a disability description")))
        case (true, true, false) => Left(List(FormError(key, s"The disability description must not exceed $maxSize characters")))
        case (true, true, true) => Right(Some(request.otherDisabilityDescriptionParam))
        case (false, _, _) => Right(None)
      }
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }


  private def bindOptionalParam[T](dependencyCheck: Boolean, validityCheck: Boolean, errMsg: String)
                                  (key: String, value: => T):Either[Seq[FormError], Option[T]] = {
    (dependencyCheck, validityCheck) match {
      case (true, false) => Left(List(FormError(key, errMsg)))
      case (true, true) => Right(Some(value))
      case (false, _) => Right(None)
    }
  }

  private def optionalParamToMap[T](key: String, optValue: Option[T]) = {
    optValue match {
      case None => Map.empty[String, String]
      case Some(value) => Map(key -> value.toString)
    }
  }

  implicit class RequestValidation(request: Map[String, String]) {
    def param(name:String) = request.collectFirst { case (key, value) if key == name => value }

    def hasDisabilityParam = param(hasDisability).getOrElse("")

    val validHasDisabilityList = Seq("Yes", "No", "I don't know/prefer not to say")
    def isHasDisabilityValid = validHasDisabilityList.contains(hasDisabilityParam)

    def disabilityImpactParam = param(disabilityImpact).getOrElse("")

    val validDisabilityImpactList = Seq("Yes, a lot", "Yes, a little", "No")
    def isDisabilityImpactValid = validDisabilityImpactList.contains(disabilityImpactParam)

    def isHasDisabilitySelected = hasDisabilityParam == "Yes"

    def disabilityCategoriesParam = request.filterKeys(_.contains(disabilityCategories)).values.toList

    def isDisabilityCategoriesValid = {
      val preferNotToSayValid = if (disabilityCategoriesParam.contains(preferNotToSay)) {
        disabilityCategoriesParam.size == 1
      } else { true }

      // At least one disability category must be selected. The chosen categories must be ones from the list.
      // If "prefer not to say" is selected then it should be the only one
      disabilityCategoriesParam.nonEmpty && disabilityCategoriesParam.diff(disabilityCategoriesList).isEmpty && preferNotToSayValid
    }

    def isOtherDisabilitySelected = isHasDisabilitySelected && disabilityCategoriesParam.contains(other)

    def otherDisabilityDescriptionParam = param(otherDisabilityDescription).getOrElse("")

    // If Other is selected then the description must not be empty
    def isOtherDisabilityDescriptionFilled = disabilityCategoriesParam.contains(other) && !otherDisabilityDescriptionParam.isEmpty

    // If Other is selected then the description must not be empty and not exceed the max size
    def isOtherDisabilityDescriptionSizeValid(max: Int) = disabilityCategoriesParam.contains(other) &&
      !otherDisabilityDescriptionParam.isEmpty && otherDisabilityDescriptionParam.length <= max
  }

  import Data._
  case class Data(
                   hasDisability: String,
                   disabilityImpact: Option[String],
                   disabilityCategories: Option[List[String]],
                   otherDisabilityDescription: Option[String],
                   guaranteedInterview: Option[String],
                   needsSupportForOnlineAssessment: Option[String],
                   needsSupportForOnlineAssessmentDescription: Option[String],
                   needsSupportAtVenue: Option[String],
                   needsSupportAtVenueDescription: Option[String],
                   needsSupportForPhoneInterview: Option[String],
                   needsSupportForPhoneInterviewDescription: Option[String]) {

    override def toString =
      s"hasDisability=$hasDisability," +
        s"disabilityImpact=$disabilityImpact," +
        s"disabilityCategories=$disabilityCategories," +
        s"otherDisabilityDescription=$otherDisabilityDescription," +
        s"guaranteedInterview=$guaranteedInterview," +
        s"needsSupportForOnlineAssessment=$needsSupportForOnlineAssessment," +
        s"needsSupportForOnlineAssessmentDescription=$needsSupportForOnlineAssessmentDescription," +
        s"needsSupportAtVenue=$needsSupportAtVenue," +
        s"needsSupportAtVenueDescription=$needsSupportAtVenueDescription," +
        s"needsSupportForPhoneInterview=$needsSupportForPhoneInterview," +
        s"needsSupportForPhoneInterviewDescription=$needsSupportForPhoneInterviewDescription"

    def exchange: AssistanceDetails = {
      AssistanceDetails(
        hasDisability,
        disabilityImpact,
        disabilityCategories,
        otherDisabilityDescription,
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
      def hasDisabilityCheck = hasDisability == "Yes"
      AssistanceDetailsForm.Data(
        hasDisability,
        if (hasDisabilityCheck) disabilityImpact else None,
        if (hasDisabilityCheck) disabilityCategories else None,
        if (hasDisabilityCheck) otherDisabilityDescription else None,
        if (hasDisabilityCheck) guaranteedInterview else None,
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
      Data(
        ad.hasDisability,
        ad.disabilityImpact,
        ad.disabilityCategories,
        ad.otherDisabilityDescription,
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
