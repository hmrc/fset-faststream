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

import connectors.exchange.{ CivilServiceExperienceDetails, GeneralDetails }
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

  def ageReference(implicit now: LocalDate) = new LocalDate(now.getYear, 8, 31)

  def maxDob(implicit now: LocalDate) = Some(ageReference.minusYears(MinAge))

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
        edipCompleted -> of(Mappings.mayBeOptionalString("error.needsEdipCompleted.required", 31, isSdipAndCreatedOrInProgress))
      )(Data.apply)(Data.unapply)
    )

  val isFastStream = (requestParams: Map[String, String]) => {
    requestParams.getOrElse("applicationRoute", ApplicationRoute.Faststream.toString) == ApplicationRoute.Faststream.toString
  }

  val isSdipFastStream = (requestParams: Map[String, String]) => {
    requestParams.getOrElse("applicationRoute", ApplicationRoute.SdipFaststream.toString) == ApplicationRoute.SdipFaststream.toString
  }

  val isSdip = (requestParams: Map[String, String]) =>
    requestParams.getOrElse("applicationRoute", Faststream.toString) == Sdip.toString

  val isCreatedOrInProgressSubmitted = (requestParams: Map[String, String]) =>
    List("CREATED", "IN_PROGRESS").contains(requestParams.getOrElse("applicationStatus", ""))

  val isSdipAndCreatedOrInProgress = (requestParams: Map[String, String]) =>
    isSdip(requestParams) && isCreatedOrInProgressSubmitted(requestParams)

  def fastPassFormFormatter(ignoreValidations: Boolean) = new Formatter[Option[FastPassForm.Data]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[FastPassForm.Data]] = {
      (ignoreValidations, isFastStream(data) || isSdipFastStream(data)) match {
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
                  edipCompleted: Option[String]
                 ) {

    def insideUk = outsideUk match {
      case Some(true) => false
      case _ => true
    }

    def toExchange(email: String, updateApplicationStatus: Option[Boolean],
                   overrideEdipCompleted: Option[Boolean] = None) = {
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
        updateApplicationStatus
      )
    }
  }
}
