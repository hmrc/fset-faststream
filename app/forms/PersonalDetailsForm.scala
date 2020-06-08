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

import connectors.exchange.GeneralDetails
import forms.Mappings._
import mappings.PhoneNumberMapping.PhoneNumber
import mappings.PostCodeMapping._
import mappings.{ Address, DayMonthYear, PhoneNumberMapping, PostCodeMapping }
import models.ApplicationRoute
import models.ApplicationRoute._
import org.joda.time.LocalDate
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }

object PersonalDetailsForm {

  private val MinAge = 16
  private val MinDob = new LocalDate(1900, 1, 1)

  val firstName = "firstName"
  val lastName = "lastName"
  val preferredName = "preferredName"
  val dateOfBirth = "dateOfBirth"
  val outsideUk = "outsideUk"
  val address = "address"
  val postCode = "postCode"
  val country = "country"
  val phone = "phone"
  val edipCompleted = "edipCompleted"
  val edipYear = "edipYear"
  val otherInternshipCompleted = "otherInternshipCompleted"
  val otherInternshipName = "otherInternshipName"
  val otherInternshipYear = "otherInternshipYear"

  def ageReference(implicit now: LocalDate) = new LocalDate(now.getYear, 8, 31)

  def maxDob(implicit now: LocalDate) = Some(ageReference.minusYears(MinAge))

  val otherInternshipNameMaxSize = 30

  def form(implicit now: LocalDate, ignoreFastPassValidations: Boolean = false) = Form(
      mapping(
        firstName -> nonEmptyTrimmedText("error.firstName", 256),
        lastName -> nonEmptyTrimmedText("error.lastName", 256),
        preferredName -> nonEmptyTrimmedText("error.preferredName", 256),
        dateOfBirth -> DayMonthYear.validDayMonthYear("error.dateOfBirth", "error.dateOfBirthInFuture")(Some(MinDob), maxDob),
        outsideUk -> optional(checked("error.address.required")),
        address -> Address.address,
        postCode -> of(postCodeFormatter),
        country -> of(countryFormatter),
        phone -> of(phoneNumberFormatter),
        FastPassForm.formQualifier -> of(fastPassFormFormatter(ignoreFastPassValidations)),
        // Relevant for sdip, sdipFs
        edipCompleted -> of(Mappings.mayBeOptionalString("error.edipCompleted.required", 31, isSdipOrSdipFsAndCreatedOrInProgress)),
        edipYear -> of(edipYearFormatter),
        // Relevant for edip, sdip, sdip faststream
        otherInternshipCompleted -> of(Mappings.mayBeOptionalString(
          "error.otherInternshipCompleted.required", "error.edipCandidate.otherInternshipCompleted.required", 31,
          isEdipOrSdipOrSdipFsAndCreatedOrInProgress, isEdipCandidate)),
        otherInternshipName -> of(otherInternshipNameFormatter(otherInternshipNameMaxSize)),
        otherInternshipYear -> of(otherInternshipYearFormatter)
      )(Data.apply)(Data.unapply)
    )

  val isSdipOrSdipFsAndCreatedOrInProgress = (requestParams: Map[String, String]) =>
    (requestParams.isSdip || requestParams.isSdipFastStream) && requestParams.isCreatedOrInProgressSubmitted

  val isEdipOrSdipOrSdipFsAndCreatedOrInProgress = (requestParams: Map[String, String]) =>
    (requestParams.isEdip || isSdipOrSdipFsAndCreatedOrInProgress(requestParams)) && requestParams.isCreatedOrInProgressSubmitted

  val isEdipCandidate = (requestParams: Map[String, String]) => requestParams.isEdip

  implicit class RequestValidation(request: Map[String, String]) {
    val isFastStream = request.getOrElse("applicationRoute", ApplicationRoute.Faststream.toString) == ApplicationRoute.Faststream.toString
    val isSdipFastStream = request.getOrElse("applicationRoute",
      ApplicationRoute.Faststream.toString) == ApplicationRoute.SdipFaststream.toString
    val isEdip = request.getOrElse("applicationRoute", Faststream.toString) == Edip.toString
    val isSdip = request.getOrElse("applicationRoute", Faststream.toString) == Sdip.toString
    val isCreatedOrInProgressSubmitted = List("CREATED", "IN_PROGRESS").contains(request.getOrElse("applicationStatus", ""))

    def param(name: String) = request.collectFirst { case (key, value) if key.contains(name) => value }

    def edipCompletedParam = param(edipCompleted).getOrElse("")
    def hasCompletedEdip = edipCompletedParam == "true"

    def edipYearParam = param(edipYear).getOrElse("")
    def isEdipInternshipYearValid = hasCompletedEdip && edipYearParam.matches("[0-9]{4}")

    def otherInternshipCompletedParam = param(otherInternshipCompleted).getOrElse("")
    def hasCompletedOtherInternship = otherInternshipCompletedParam == "true"

    def otherInternshipNameParam = param(otherInternshipName).getOrElse("")
    def isOtherInternshipNameFilled = hasCompletedOtherInternship && otherInternshipNameParam.length > 0
    def isOtherInternshipNameSizeValid(max: Int) = hasCompletedOtherInternship &&
      isOtherInternshipNameFilled && otherInternshipNameParam.length <= max

    def otherInternshipYearParam = param(otherInternshipYear).getOrElse("")
    def isOtherInternshipYearValid = hasCompletedOtherInternship && otherInternshipYearParam.matches("[0-9]{4}")
  }

  def edipYearFormatter = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      bindOptionalParam(request.hasCompletedEdip, request.isEdipInternshipYearValid,
        "error.edipYear.required")(key, request.edipYearParam)
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }

  def otherInternshipNameFormatter(maxSize: Int) = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {

      val dependencyCheck = request.hasCompletedOtherInternship
      val isFilled = request.isOtherInternshipNameFilled
      val isCorrectSize = request.isOtherInternshipNameSizeValid(maxSize)

      (dependencyCheck, isFilled, isCorrectSize) match {
        case (true, false, _) => Left(List(FormError(key, "error.otherInternshipName.required", Seq(maxSize))))
        case (true, true, false) => Left(List(FormError(key, "error.otherInternshipName.size")))
        case (true, true, true) => Right(Some(request.otherInternshipNameParam))
        case (false, _, _) => Right(None)
      }
    }

    def unbind(key: String, value: Option[String]): Map[String, String] = optionalParamToMap(key, value)
  }

  def otherInternshipYearFormatter = new Formatter[Option[String]] {
    def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      bindOptionalParam(request.hasCompletedOtherInternship, request.isOtherInternshipYearValid,
        "error.otherInternshipYear.required")(key, request.otherInternshipYearParam)
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

  def fastPassFormFormatter(ignoreValidations: Boolean) = new Formatter[Option[FastPassForm.Data]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[FastPassForm.Data]] = {
      (ignoreValidations, data.isFastStream) match {
        case (false, true) => FastPassForm.form.mapping.bind(data).right.map(Some(_))
        case _ => Right(None)
      }
    }

    override def unbind(key: String, fastPassData: Option[FastPassForm.Data]) =
      fastPassData.map(fpd => FastPassForm.form.fill(fpd).data).getOrElse(Map(key -> ""))
  }

  val phoneNumberFormatter = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val phone: Option[String] = data.get(key)

      phone match {
        case None | Some("") => Left(List(FormError(key, "error.phone.required")))
        case Some(m) if !m.isEmpty && !PhoneNumberMapping.validatePhoneNumber(m) => Left(List(FormError(key, "error.phoneNumber.format")))
        case _ => Right(phone.map(_.trim))
      }
    }

    override def unbind(key: String, value: Option[String]) = Map(key -> value.map(_.trim).getOrElse(""))
  }

  val postCodeFormatter = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val outsideUK = data.getOrElse(outsideUk, "false").toBoolean
      val postCode = data.getOrElse(key, "").trim
      outsideUK match {
        case true => Right(None)
        case _ if postCode.isEmpty => Left(List(FormError(key, "error.postcode.required")))
        case _ if !postcodePattern.pattern.matcher(postCode).matches() => Left(List(FormError(key, "error.postcode.invalid")))
        case _ => Right(Some(postCode))
      }
    }

    override def unbind(key: String, value: Option[String]) = Map(key -> value.getOrElse(""))
  }

  val countryFormatter = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val outsideUK = data.getOrElse(outsideUk, "false").toBoolean
      val country = data.getOrElse(key, "").trim
      outsideUK match {
        case true if country.isEmpty => Left(List(FormError(key, "error.country.required")))
        case true if country.length > 100 => Left(List(FormError(key, "error.country.invalid")))
        case true => Right(Some(country))
        case _ => Right(None)
      }
    }

    override def unbind(key: String, value: Option[String]) = Map(key -> value.getOrElse(""))
  }

  case class Data(firstName: String,
                  lastName: String,
                  preferredName: String,
                  dateOfBirth: DayMonthYear,
                  outsideUk: Option[Boolean],
                  address: Address,
                  postCode: Option[PostCode],
                  country: Option[String],
                  phone: Option[PhoneNumber],
                  civilServiceExperienceDetails: Option[FastPassForm.Data],
                  edipCompleted: Option[String],
                  edipYear: Option[String],
                  otherInternshipCompleted: Option[String],
                  otherInternshipName: Option[String],
                  otherInternshipYear: Option[String]
                 ) {

    def insideUk = outsideUk match {
      case Some(true) => false
      case _ => true
    }

    def toExchange(email: String, updateApplicationStatus: Option[Boolean],
                   overrideEdipCompleted: Option[Boolean] = None, overrideOtherInternshipCompleted: Option[Boolean] = None) = {
      GeneralDetails(
        firstName,
        lastName,
        preferredName,
        email,
        LocalDate.parse(s"${dateOfBirth.year}-${dateOfBirth.month}-${dateOfBirth.day}"),
        outsideUk.getOrElse(false),
        address,
        postCode.map(p => PostCodeMapping.formatPostcode(p)),
        fsacIndicator = None, // It is calculated in the backend.
        country,
        phone,
        civilServiceExperienceDetails,
        overrideEdipCompleted.orElse(edipCompleted.map(_.toBoolean)),
        edipYear,
        overrideOtherInternshipCompleted.orElse(otherInternshipCompleted.map(_.toBoolean)),
        otherInternshipName,
        otherInternshipYear,
        updateApplicationStatus
      )
    }
  }
}
