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

import connectors.exchange.{ CivilServiceExperienceDetails, GeneralDetails }
import forms.Mappings._
import mappings.PhoneNumberMapping.PhoneNumber
import mappings.PostCodeMapping._
import mappings.{ Address, DayMonthYear, PhoneNumberMapping, PostCodeMapping }
import org.joda.time.LocalDate
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }

object GeneralDetailsForm {
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

  def ageReference(implicit now: LocalDate) = new LocalDate(now.getYear, 8, 31)

  def maxDob(implicit now: LocalDate) = Some(ageReference.minusYears(MinAge))

  def form(implicit now: LocalDate, ignoreFastPassValidations: Boolean = false) = {
    val fastPassFormMapping = if(ignoreFastPassValidations) FastPassForm.ignoreForm.mapping else FastPassForm.form.mapping

    Form(
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
        FastPassForm.formQualifier -> optional(fastPassFormMapping)
      )(Data.apply)(Data.unapply)
    )
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
                  civilServiceExperienceDetails: Option[FastPassForm.Data]
                 ) {

    def insideUk = outsideUk match {
      case Some(true) => false
      case _ => true
    }

    def toExchange(email: String, updateApplicationStatus: Option[Boolean],
                   overrideCivilServiceExperienceDetails: Option[CivilServiceExperienceDetails] = None) = GeneralDetails(
      firstName,
      lastName,
      preferredName,
      email,
      LocalDate.parse(s"${dateOfBirth.year}-${dateOfBirth.month}-${dateOfBirth.day}"),
      outsideUk.getOrElse(false),
      address,
      postCode.map(p => PostCodeMapping.formatPostcode(p)),
      country,
      phone,
      overrideCivilServiceExperienceDetails.orElse(civilServiceExperienceDetails),
      updateApplicationStatus
    )
  }

}
