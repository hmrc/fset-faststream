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

import forms.Mappings._
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
    CivilServant -> "I'm currently a civil servant",
    CivilServantViaFastTrack -> "I'm currently a civil servant via the Fast Track",
    DiversityInternship -> "I've completed SDIP or EDIP"
  )

  val InternshipTypes = Seq(
    EDIP -> "Early Diversity Internship Programme",
    SDIPPreviousYear -> "Summer Diversity Internship Programme (Previous years)",
    SDIPCurrentYear -> "Summer Diversity Internship Programme 2016 (This year)"
  )

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

  val getKeyQualifier = (formKey:String) => {
    formKey.split('.') match {
      case splits if splits.length > 1 => splits(0) + "."
      case _ => ""
    }
  }

  def fastPassTypeFormatter = new Formatter[Option[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val keyQualifier = getKeyQualifier(key)
      (data.isApplicable(keyQualifier), data.isValidFastPassTypeSelected(keyQualifier)) match {
        case (true, false) => Left(List(FormError(key, Messages("error.fastPassType.required"))))
        case (false, _) => Right(None)
        case _ => Right(Some(data.getFastPassType(keyQualifier)))
      }
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = {
      value match {
        case None => Map.empty[String, String]
        case Some(fpType) => Map(key -> fpType)
      }
    }
  }

  def internshipTypesFormatter = new Formatter[Option[Seq[String]]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[Seq[String]]] = {
      val keyQualifier = getKeyQualifier(key)
      (data.isDiversityInternshipSelected(keyQualifier), data.isValidInternshipTypesSelected(keyQualifier)) match {
        case (true, false) => Left(List(FormError(key, Messages("error.internshipTypes.required"))))
        case (true, true) => Right(Some(data.getInternshipTypes(keyQualifier)))
        case (false, _) => Right(None)
      }
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
      val keyQualifier = getKeyQualifier(key)
      (data.isSDIPCurrentYearSelected(keyQualifier), data.isFastPassReceivedValid(keyQualifier)) match {
        case (true, false) => Left(List(FormError(key, Messages("error.fastPassReceived.required"))))
        case (true, true) => Right(Some(data.getFastPassReceived(keyQualifier).toBoolean))
        case (false, _) => Right(None)
      }
    }

    def unbind(key: String, value: Option[Boolean]): Map[String, String] = {
      value match {
        case None => Map.empty[String, String]
        case Some(fpReceived) => Map(key -> fpReceived.toString)
      }
    }
  }

  def fastPassCertificateFormatter = new Formatter[Option[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val keyQualifier = getKeyQualifier(key)
      (data.isFastPassReceived(keyQualifier), data.isCertificateNumberValid(keyQualifier)) match {
        case (true, false) => Left(List(FormError(key, Messages("error.certificateNumber.required"))))
        case (true, true) => Right(Some(data.getCertificateNumber(keyQualifier)))
        case (false, _) => Right(None)
      }
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = {
      value match {
        case None => Map.empty[String, String]
        case Some(certNum) => Map(key -> certNum)
      }
    }
  }

  implicit class DataValidations(data:Map[String, String]){

    val getApplicable = (keyQualifier:String) => data.getOrElse(s"$keyQualifier$applicable", "")

    val getFastPassType = (keyQualifier:String) => data.getOrElse(s"$keyQualifier$fastPassType", "")

    val getInternshipTypes = (keyQualifier:String) => data.filterKeys(_.contains(s"$keyQualifier$internshipTypes")).values.toSeq

    val getFastPassReceived = (keyQualifier:String) => data.getOrElse(s"$keyQualifier$fastPassReceived", "")

    val getCertificateNumber = (keyQualifier:String) => data.getOrElse(s"$keyQualifier$certificateNumber", "")

    val isValidFastPassTypeSelected = (keyQualifier:String) =>
      FastPassTypes.map(_._1).contains(getFastPassType(keyQualifier))

    val isValidInternshipTypesSelected = (keyQualifier:String) => {
      val internshipTypes = getInternshipTypes(keyQualifier)
      internshipTypes.nonEmpty && internshipTypes.diff(InternshipTypes.toMap.keys.toSeq).isEmpty
    }

    val isFastPassReceivedValid = (keyQualifier:String) =>
      getFastPassReceived(keyQualifier) == "true" || getFastPassReceived(keyQualifier) == "false"

    val isCertificateNumberValid = (keyQualifier:String) =>
      getCertificateNumber(keyQualifier).matches("[0-9]{7}")

    val isApplicable = (keyQualifier:String) => getApplicable(keyQualifier) == "true"

    val isDiversityInternshipSelected = (keyQualifier:String) =>
      isApplicable(keyQualifier) && DiversityInternship == getFastPassType(keyQualifier)

    val isSDIPCurrentYearSelected = (keyQualifier:String) =>
      isDiversityInternshipSelected(keyQualifier) && getInternshipTypes(keyQualifier).contains(SDIPCurrentYear)


    val isFastPassReceived = (keyQualifier:String) =>
      isSDIPCurrentYearSelected(keyQualifier) && getFastPassReceived(keyQualifier) == "true"

    def cleanupFastPassFields(keyQualifier:String = formQualifier + ".") = data.filterKeys {
      case key if key.endsWith(fastPassType) => isApplicable(keyQualifier)
      case key if key.contains(internshipTypes) => isDiversityInternshipSelected(keyQualifier)
      case key if key.endsWith(fastPassReceived) => isSDIPCurrentYearSelected(keyQualifier)
      case key if key.endsWith(certificateNumber) => isFastPassReceived(keyQualifier)
      case _ => true
    }
  }
}
