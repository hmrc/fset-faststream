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

package forms

import mappings.Mappings._
import models.ApplicationRoute
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages

object FastPassForm {

  val EmptyCivilServiceExperienceDetails: Option[Data] = Some(Data("", None, None, None))

  val CivilServant = "CivilServant"
  val CivilServantViaFastTrack = "CivilServantViaFastTrack"
  val DiversityInternship = "DiversityInternship"

  def sdipFsCivilServiceExperienceTypes(implicit messages: Messages) = Seq(
    CivilServant -> Messages("civilServiceExperienceType.CivilServant"),
    CivilServantViaFastTrack -> Messages("civilServiceExperienceType.CivilServantViaFastTrack"),
    DiversityInternship -> Messages("civilServiceExperienceType.EdipInternship")
  )

  val CivilServantKey = "CivilServant"
  val EDIPKey = "EDIP"
  val SDIPKey = "SDIP"
  val OtherInternshipKey = "OtherInternship"
  def civilServantAndInternshipTypes(implicit messages: Messages) = Seq(
    CivilServantKey -> Messages("civilServantAndInternshipType.civilServant"),
    EDIPKey -> Messages("civilServantAndInternshipType.EDIP"),
    SDIPKey -> Messages("civilServantAndInternshipType.SDIP"),
    OtherInternshipKey -> Messages("civilServantAndInternshipType.another")
  )

  val edipInternshipYear = "edipYear"
  def edipInternshipYearMsg(implicit messages: Messages) = Messages("error.edipInternshipYear.required")

  val sdipInternshipYear = "sdipYear"
  def sdipInternshipYearMsg(implicit messages: Messages) = Messages("error.sdipInternshipYear.required")

  val otherInternshipName = "otherInternshipName"
  def otherInternshipNameMsg(implicit messages: Messages) = Messages("error.otherInternshipName.required")
  val otherInternshipNameMaxSize = 60
  def otherInternshipNameSizeMsg(implicit messages: Messages) = Messages("error.otherInternshipName.size", otherInternshipNameMaxSize)

  val otherInternshipYear = "otherInternshipYear"
  def otherInternshipYearMsg(implicit messages: Messages) = Messages("error.otherInternshipYear.required")

  def civilServantAndInternshipTypeRequiredMsg(implicit messages: Messages) = Messages("error.civilServantAndInternshipTypes.required")

  def fastPassReceivedRequiredMsg(implicit messages: Messages) = Messages("error.fastPassReceived.required")
  def certificateNumberRequiredMsg(implicit messages: Messages) = Messages("error.certificateNumber.required")

  val formQualifier = "civilServiceExperienceDetails"
  val applicable = "applicable"
  val civilServantAndInternshipTypesKey = "civilServantAndInternshipTypes"
  val fastPassReceived = "fastPassReceived"
  val certificateNumber = "certificateNumber"

  case class Data(applicable: String,
                  civilServantAndInternshipTypes: Option[Seq[String]] = None,
                  edipYear: Option[String] = None,
                  sdipYear: Option[String] = None,
                  otherInternshipName: Option[String] = None,
                  otherInternshipYear: Option[String] = None,
                  fastPassReceived: Option[Boolean] = None,
                  certificateNumber: Option[String] = None)

  def form(implicit messages: Messages) = {
    Form(mapping(
      s"$formQualifier.applicable" -> nonemptyBooleanText("error.applicable.required"),
      s"$formQualifier.civilServantAndInternshipTypes" -> of(civilServantAndInternshipTypesFormatter),
      s"$formQualifier.edipYear" -> of(edipInternshipYearFormatter),
      s"$formQualifier.sdipYear" -> of(sdipInternshipYearFormatter),
      s"$formQualifier.otherInternshipName" -> of(otherInternshipNameFormatter(otherInternshipNameMaxSize)),
      s"$formQualifier.otherInternshipYear" -> of(otherInternshipYearFormatter),
      s"$formQualifier.fastPassReceived" -> of(fastPassReceivedFormatter),
      s"$formQualifier.certificateNumber" -> of(fastPassCertificateFormatter)
    )(Data.apply)(Data.unapply))
  }

  // Only applicable for fs candidates - all other application routes have validation on the PersonalDetailsForm
  def civilServantAndInternshipTypesFormatter(implicit messages: Messages) = new Formatter[Option[Seq[String]]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[Seq[String]]] = {
      bindOptionalParam(request.isCivilServantOrIntern && request.isFaststream, request.isValidCivilServantAndInternshipTypeSelected,
        civilServantAndInternshipTypeRequiredMsg)(key, request.civilServantAndInternshipTypesParam)
    }

    def unbind(key: String, value: Option[Seq[String]]): Map[String, String] = {
      value match {
        case Some(seq) => seq.zipWithIndex.foldLeft(Map.empty[String, String])(
          (res, pair) => res + (s"$key[${pair._2}]" -> pair._1))
        case None => Map.empty[String, String]
      }
    }
  }

  def sdipInternshipYearFormatter(implicit messages: Messages) = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      bindOptionalParam(request.isSdipCandidate, request.isSdipInternshipYearValid,
        sdipInternshipYearMsg)(key, request.sdipInternshipYearParam)
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }

  def edipInternshipYearFormatter(implicit messages: Messages) = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      bindOptionalParam(request.isEdipCandidate, request.isEdipInternshipYearValid,
        edipInternshipYearMsg)(key, request.edipInternshipYearParam)
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }

  def otherInternshipNameFormatter(maxSize: Int)(implicit messages: Messages) = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {

      val dependencyCheck = request.isOtherInternshipCandidate
      val isFilled = request.isOtherInternshipNameFilled
      val isCorrectSize = request.isOtherInternshipNameSizeValid(maxSize)

      (dependencyCheck, isFilled, isCorrectSize) match {
        case (true, false, _) => Left(List(FormError(key, otherInternshipNameMsg)))
        case (true, true, false) => Left(List(FormError(key, otherInternshipNameSizeMsg)))
        case (true, true, true) => Right(Some(request.otherInternshipNameParam))
        case (false, _, _) => Right(None)
      }
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }

  def otherInternshipYearFormatter(implicit messages: Messages) = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      bindOptionalParam(request.isOtherInternshipCandidate, request.isOtherInternshipYearValid,
        otherInternshipYearMsg)(key, request.otherInternshipYearParam)
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }

  def fastPassReceivedFormatter(implicit messages: Messages) = new Formatter[Option[Boolean]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[Boolean]] = {
      bindOptionalParam(request.isCivilServantOrIntern, request.isFastPassReceivedValid,
        fastPassReceivedRequiredMsg)(key, request.fastPassReceivedParam.toBoolean)
    }

    def unbind(key: String, value: Option[Boolean]): Map[String, String] = optionalParamToMap(key, value)
  }

  def fastPassCertificateFormatter(implicit messages: Messages) = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      bindOptionalParam(request.isFastPassReceived, request.isCertificateNumberValid,
        certificateNumberRequiredMsg)(key, request.certificateNumberParam)
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }

  private def bindOptionalParam[T](dependencyCheck: Boolean, validityCheck: Boolean, errMsg: String)
                                  (key: String, value: => T): Either[Seq[FormError], Option[T]] =
    (dependencyCheck, validityCheck) match {
      case (true, false) => Left(List(FormError(key, errMsg)))
      case (true, true) => Right(Some(value))
      case (false, _) => Right(None)
    }

  private def optionalParamToMap[T](key: String, optValue: Option[T]) = {
    optValue match {
      case None => Map.empty[String, String]
      case Some(value) => Map(key -> value.toString)
    }
  }

  implicit class RequestValidation(request: Map[String, String]) {

    def param(name: String) = request.collectFirst { case (key, value) if key.endsWith(name) => value }

    def fastPassApplicableParam = param(applicable).getOrElse("")

    def isCivilServantOrIntern = fastPassApplicableParam == "true"

    def isFaststream = request.get("applicationRoute").contains(ApplicationRoute.Faststream.toString)

    def civilServantAndInternshipTypesParam(implicit messages: Messages) = {
      request.filterKeys(_.contains(civilServantAndInternshipTypesKey)).values.toSeq
    }

    def isValidCivilServantAndInternshipTypeSelected(implicit messages: Messages) = {
      val filled = civilServantAndInternshipTypesParam.nonEmpty
      val isValid = civilServantAndInternshipTypesParam.diff(civilServantAndInternshipTypes.toMap.keys.toSeq).isEmpty
      filled && isValid
    }

    // Sdip
    def sdipInternshipYearParam = param(sdipInternshipYear).getOrElse("")

    def isSdipCandidate(implicit messages: Messages) = isCivilServantOrIntern && isSdipInternshipSelected

    def isSdipInternshipSelected(implicit messages: Messages) = civilServantAndInternshipTypesParam.contains(SDIPKey)

    def isSdipInternshipYearValid(implicit messages: Messages) = isSdipInternshipSelected && sdipInternshipYearParam.matches("[0-9]{4}")

    // Edip
    def edipInternshipYearParam = param(edipInternshipYear).getOrElse("")

    def isEdipCandidate(implicit messages: Messages) = isCivilServantOrIntern && civilServantAndInternshipTypesParam.contains(EDIPKey)

    def isEdipInternshipSelected(implicit messages: Messages) = civilServantAndInternshipTypesParam.contains(EDIPKey)

    def isEdipInternshipYearValid(implicit messages: Messages) = isEdipInternshipSelected && edipInternshipYearParam.matches("[0-9]{4}")

    // Other internship
    def isOtherInternshipSelected(implicit messages: Messages) = civilServantAndInternshipTypesParam.contains(OtherInternshipKey)

    def isOtherInternshipCandidate(implicit messages: Messages) = isCivilServantOrIntern && isOtherInternshipSelected

    // Other internship name
    def otherInternshipNameParam = param(otherInternshipName).getOrElse("")

    def isOtherInternshipNameFilled(implicit messages: Messages) = isOtherInternshipSelected && otherInternshipNameParam.length > 0

    def isOtherInternshipNameSizeValid(max: Int)(implicit messages: Messages) = isOtherInternshipSelected &&
      isOtherInternshipNameFilled && otherInternshipNameParam.length <= max

    // Other internship year
    def otherInternshipYearParam = param(otherInternshipYear).getOrElse("")

    def isOtherInternshipYearValid(implicit messages: Messages) = isOtherInternshipSelected && otherInternshipYearParam.matches("[0-9]{4}")

    // Fast pass received
    def fastPassReceivedParam = param(fastPassReceived).getOrElse("")

    def isFastPassReceived = isCivilServantOrIntern && fastPassReceivedParam == "true"

    def isFastPassReceivedValid = fastPassReceivedParam == "true" || fastPassReceivedParam == "false"

    // Fast pass certificate
    def certificateNumberParam = param(certificateNumber).getOrElse("")

    def isCertificateNumberValid = certificateNumberParam.matches("[0-9]{7}")

    // Removes child data that is dependent on a parent if that parent has not been selected
    //scalastyle:off cyclomatic.complexity
    def cleanupFastPassFields(implicit messages: Messages) = request.filterKeys {
      case key if key.contains("civilServantAndInternshipTypes") || key.contains("fastPassReceived") => isCivilServantOrIntern
      case key if key.endsWith("sdipYear") => isSdipCandidate
      case key if key.endsWith("otherInternshipName") || key.endsWith("otherInternshipYear") => isOtherInternshipCandidate
      case key if key.endsWith("edipYear") => isEdipCandidate
      case key if key.endsWith("certificateNumber") => isFastPassReceived
      case _ => true
    } //scalastyle:on
  }
}
