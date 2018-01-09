/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.data.Forms._
import Mappings._
import models.ApplicationRoute
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

object FastPassForm {

  val EmptyCivilServiceExperienceDetails: Option[Data] = Some(Data("", None, None, None, None))

  val CivilServant = "CivilServant"
  val CivilServantViaFastTrack = "CivilServantViaFastTrack"
  val DiversityInternship = "DiversityInternship"

  val EDIP = "EDIP"
  val SDIPPreviousYear = "SDIPPreviousYear"
  val SDIPCurrentYear = "SDIPCurrentYear"

  val CivilServiceExperienceTypes = Seq(
    CivilServant -> Messages("civilServiceExperienceType.CivilServant"),
    CivilServantViaFastTrack -> Messages("civilServiceExperienceType.CivilServantViaFastTrack"),
    DiversityInternship -> Messages("civilServiceExperienceType.DiversityInternship")
  )

  val SdipFsCivilServiceExperienceTypes = Seq(
    CivilServant -> Messages("civilServiceExperienceType.CivilServant"),
    CivilServantViaFastTrack -> Messages("civilServiceExperienceType.CivilServantViaFastTrack"),
    DiversityInternship -> Messages("civilServiceExperienceType.EdipInternship")
  )

  val InternshipTypes = Seq(
    EDIP -> Messages("internshipType.EDIP"),
    SDIPPreviousYear -> Messages("internshipType.SDIPPreviousYear"),
    SDIPCurrentYear -> Messages("internshipType.SDIPCurrentYear")
  )

  val civilServiceExperienceTypeRequiredMsg = Messages("error.civilServiceExperienceType.required")
  val internshipTypeRequiredMsg = Messages("error.internshipTypes.required")
  val fastPassReceivedRequiredMsg = Messages("error.fastPassReceived.required")
  val certificateNumberRequiredMsg = Messages("error.certificateNumber.required")

  val formQualifier = "civilServiceExperienceDetails"
  val applicable = "applicable"
  val civilServiceExperienceType = "civilServiceExperienceType"
  val internshipTypes = "internshipTypes"
  val fastPassReceived = "fastPassReceived"
  val certificateNumber = "certificateNumber"

  case class Data(applicable: String,
                  civilServiceExperienceType: Option[String] = None,
                  internshipTypes: Option[Seq[String]] = None,
                  fastPassReceived: Option[Boolean] = None,
                  certificateNumber: Option[String] = None)

  def form = {
    Form(mapping(
      s"$formQualifier.applicable" -> nonemptyBooleanText("error.applicable.required"),
      s"$formQualifier.civilServiceExperienceType" -> of(civilServiceExperienceTypeFormatter),
      s"$formQualifier.internshipTypes" -> of(internshipTypesFormatter),
      s"$formQualifier.fastPassReceived" -> of(fastPassReceivedFormatter),
      s"$formQualifier.certificateNumber" -> of(fastPassCertificateFormatter)
    )(Data.apply)(Data.unapply))
  }

  def civilServiceExperienceTypeFormatter = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      bindOptionalParam(request.isCivilServantOrFastTrackOrIntern, request.isValidCivilServiceExperienceTypeSelected,
        civilServiceExperienceTypeRequiredMsg)(key, request.civilServiceExperienceTypeParam)
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }

  def internshipTypesFormatter = new Formatter[Option[Seq[String]]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[Seq[String]]] = {
      bindOptionalParam(request.isDiversityInternshipSelected && !request.isSdipFaststream, request.isValidInternshipTypesSelected,
        internshipTypeRequiredMsg)(key, request.internshipTypesParam)
    }

    def unbind(key: String, value: Option[Seq[String]]): Map[String, String] = {
      value match {
        case Some(seq) => seq.zipWithIndex.foldLeft(Map.empty[String, String])(
          (res, pair) => res + (s"$key[${pair._2}]" -> pair._1))
        case None => Map.empty[String, String]
      }
    }
  }

  def fastPassReceivedFormatter = new Formatter[Option[Boolean]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[Boolean]] = {
      bindOptionalParam(request.isSDIPCurrentYearSelected, request.isFastPassReceivedValid,
        fastPassReceivedRequiredMsg)(key, request.fastPassReceivedParam.toBoolean)
    }

    def unbind(key: String, value: Option[Boolean]): Map[String, String] = optionalParamToMap(key, value)
  }

  def fastPassCertificateFormatter = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      bindOptionalParam(request.isFastPassReceived, request.isCertificateNumberValid,
        certificateNumberRequiredMsg) (key, request.certificateNumberParam)
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }

  private def bindOptionalParam[T](dependencyCheck: Boolean, validityCheck: Boolean, errMsg: String)
                                  (key: String, value: => T):Either[Seq[FormError], Option[T]] =
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

    def param(name:String) = request.collectFirst { case (key, value) if key.endsWith(name) => value }

    def fastPassApplicableParam = param(applicable).getOrElse("")

    def civilServiceExperienceTypeParam = param(civilServiceExperienceType).getOrElse("")

    def isSdipFaststream = request.get("applicationRoute").contains(ApplicationRoute.SdipFaststream.toString)

    def internshipTypesParam = request.filterKeys(_.contains(internshipTypes)).values.toSeq

    def fastPassReceivedParam = param(fastPassReceived).getOrElse("")

    def certificateNumberParam = param(certificateNumber).getOrElse("")

    def isValidCivilServiceExperienceTypeSelected = CivilServiceExperienceTypes.map(_._1).contains(civilServiceExperienceTypeParam)

    def isValidInternshipTypesSelected = internshipTypesParam.nonEmpty && internshipTypesParam.diff(InternshipTypes.toMap.keys.toSeq).isEmpty

    def isFastPassReceivedValid = fastPassReceivedParam == "true" || fastPassReceivedParam == "false"

    def isCertificateNumberValid = certificateNumberParam.matches("[0-9]{7}")

    def isCivilServantOrFastTrackOrIntern = fastPassApplicableParam == "true"

    def isDiversityInternshipSelected = isCivilServantOrFastTrackOrIntern && (civilServiceExperienceTypeParam == DiversityInternship)

    def isSDIPCurrentYearSelected = isDiversityInternshipSelected && internshipTypesParam.contains(SDIPCurrentYear)

    def isFastPassReceived = isSDIPCurrentYearSelected && (fastPassReceivedParam == "true")

    def cleanupFastPassFields = request.filterKeys {
      case key if key.endsWith(civilServiceExperienceType) => isCivilServantOrFastTrackOrIntern
      case key if key.contains(internshipTypes) => isDiversityInternshipSelected
      case key if key.endsWith(fastPassReceived) => isSDIPCurrentYearSelected
      case key if key.endsWith(certificateNumber) => isFastPassReceived
      case _ => true
    }
  }
}
