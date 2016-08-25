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

import _root_.forms.Mappings._
import play.api.data.{ Form, FormError }
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.i18n.Messages

object FastPassForm {

  val EmptyFastPassDetails: Data = Data("", None, None, None, None)

  val CivilServant = "CivilServant"
  val CivilServantViaFastTrack = "CivilServantViaFastTrack"
  val DiversityInternship = "DiversityInternship"

  val EDIP = "EDIP"
  val SDIPPreviousYear = "SDIPPreviousYear"
  val SDIPCurrentYear = "SDIPCurrentYear"

  val FastPassTypes = Seq(
    CivilServant -> Messages("fastPassType.CivilServant"),
    CivilServantViaFastTrack -> Messages("fastPassType.CivilServantViaFastTrack"),
    DiversityInternship -> Messages("fastPassType.DiversityInternship")
  )

  val InternshipTypes = Seq(
    EDIP -> Messages("internshipType.EDIP"),
    SDIPPreviousYear -> Messages("internshipType.SDIPPreviousYear"),
    SDIPCurrentYear -> Messages("internshipType.SDIPCurrentYear")
  )

  val fastPassTypeRequiredMsg = Messages("error.fastPassType.required")
  val internshipTypeRequiredMsg = Messages("error.internshipTypes.required")
  val fastPassReceivedRequiredMsg = Messages("error.fastPassReceived.required")
  val certificateNumberRequiredMsg = Messages("error.certificateNumber.required")

  val formQualifier = "fastPassDetails"
  val applicable = "applicable"
  val fastPassType = "fastPassType"
  val internshipTypes = "internshipTypes"
  val fastPassReceived = "fastPassReceived"
  val certificateNumber = "certificateNumber"

  case class Data(applicable: String,
                  fastPassType: Option[String] = None,
                  internshipTypes: Option[Seq[String]] = None,
                  fastPassReceived: Option[Boolean] = None,
                  certificateNumber: Option[String] = None)

  def form = {
    Form(mapping(
      applicable -> nonemptyBooleanText("error.applicable.required"),
      fastPassType -> of(fastPassTypeFormatter),
      internshipTypes -> of(internshipTypesFormatter),
      fastPassReceived -> of(fastPassReceivedFormatter),
      certificateNumber -> of(fastPassCertificateFormatter)
    )(Data.apply)(Data.unapply))
  }

  def getKeyQualifier(formKey: String) = formKey.split('.') match {
    case splits if splits.length > 1 => splits(0) + "."
    case _ => ""
  }

  def fastPassTypeFormatter = new Formatter[Option[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      implicit val keyQualifier = getKeyQualifier(key)
      bindOptionalParam(data.isApplicable, data.isValidFastPassTypeSelected,
        fastPassTypeRequiredMsg)(key, data.getFastPassType)
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)

  }

  def internshipTypesFormatter = new Formatter[Option[Seq[String]]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[Seq[String]]] = {
      implicit val keyQualifier = getKeyQualifier(key)
      bindOptionalParam(data.isDiversityInternshipSelected, data.isValidInternshipTypesSelected,
        internshipTypeRequiredMsg)(key, data.getInternshipTypes)
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
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[Boolean]] = {
      implicit val keyQualifier = getKeyQualifier(key)
      bindOptionalParam(data.isSDIPCurrentYearSelected, data.isFastPassReceivedValid,
        fastPassReceivedRequiredMsg)(key, data.getFastPassReceived.toBoolean)
    }

    def unbind(key: String, value: Option[Boolean]): Map[String, String] = optionalParamToMap(key, value)

  }

  def fastPassCertificateFormatter = new Formatter[Option[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      implicit val keyQualifier = getKeyQualifier(key)
      bindOptionalParam(data.isFastPassReceived, data.isCertificateNumberValid,
        certificateNumberRequiredMsg) (key, data.getCertificateNumber)
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

  implicit class DataValidations(data: Map[String, String]) {

    def getApplicable(implicit keyQualifier: String) = data.getOrElse(s"$keyQualifier$applicable", "")

    def getFastPassType(implicit keyQualifier: String) = data.getOrElse(s"$keyQualifier$fastPassType", "")

    def getInternshipTypes(implicit keyQualifier: String) = data.filterKeys(_.contains(s"$keyQualifier$internshipTypes")).values.toSeq

    def getFastPassReceived(implicit keyQualifier: String) = data.getOrElse(s"$keyQualifier$fastPassReceived", "")

    def getCertificateNumber(implicit keyQualifier: String) = data.getOrElse(s"$keyQualifier$certificateNumber", "")

    def isValidFastPassTypeSelected(implicit keyQualifier: String) =
      FastPassTypes.map(_._1).contains(getFastPassType(keyQualifier))

    def isValidInternshipTypesSelected(implicit keyQualifier: String) = {
      val internshipTypes = getInternshipTypes(keyQualifier)
      internshipTypes.nonEmpty && internshipTypes.diff(InternshipTypes.toMap.keys.toSeq).isEmpty
    }

    def isFastPassReceivedValid(implicit keyQualifier: String) =
      getFastPassReceived(keyQualifier) == "true" || getFastPassReceived(keyQualifier) == "false"

    def isCertificateNumberValid(implicit keyQualifier: String) = getCertificateNumber(keyQualifier).matches("[0-9]{7}")

    def isApplicable(implicit keyQualifier: String) = getApplicable(keyQualifier) == "true"

    def isDiversityInternshipSelected(implicit keyQualifier: String) =
      isApplicable(keyQualifier) && DiversityInternship == getFastPassType(keyQualifier)

    def isSDIPCurrentYearSelected(implicit keyQualifier: String) =
      isDiversityInternshipSelected(keyQualifier) && getInternshipTypes(keyQualifier).contains(SDIPCurrentYear)

    def isFastPassReceived(implicit keyQualifier: String) =
      isSDIPCurrentYearSelected(keyQualifier) && getFastPassReceived(keyQualifier) == "true"

    def cleanupFastPassFields(keyQualifier: String = formQualifier + ".") = data.filterKeys {
      case key if key.endsWith(fastPassType) => isApplicable(keyQualifier)
      case key if key.contains(internshipTypes) => isDiversityInternshipSelected(keyQualifier)
      case key if key.endsWith(fastPassReceived) => isSDIPCurrentYearSelected(keyQualifier)
      case key if key.endsWith(certificateNumber) => isFastPassReceived(keyQualifier)
      case _ => true
    }
  }

}
