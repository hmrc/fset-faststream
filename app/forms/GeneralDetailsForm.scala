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
import mappings.PhoneNumberMapping.PhoneNumber
import mappings.PostCodeMapping.{ PostCode, validPostcode }
import mappings.{ Address, DayMonthYear, PhoneNumberMapping }
import org.joda.time.LocalDate
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }

object GeneralDetailsForm {
  val minAge = 16
  val minDob = new LocalDate(1900, 1, 1)
  def ageReference(implicit now: LocalDate) = new LocalDate(now.getYear, 8, 31)

  val phoneNumberFormatter = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val phone: Option[String] = data.get("phone")

      phone match {
        case None | Some("") => Left(List(FormError("phone", "error.phone.required")))
        case Some(m) if !m.isEmpty && !PhoneNumberMapping.validatePhoneNumber(m) => Left(List(FormError("phone", "error.phoneNumber.format")))
        case _ => Right(phone.map(_.trim))
      }
    }

    override def unbind(key: String, value: Option[String]) = Map(key -> value.map(_.trim).getOrElse(""))
  }

  def form(implicit now: LocalDate) = {
    val maxDob = Some(ageReference.minusYears(minAge))

    Form(
      mapping(
        "firstName" -> nonEmptyTrimmedText("error.firstName", 256),
        "lastName" -> nonEmptyTrimmedText("error.lastName", 256),
        "preferredName" -> nonEmptyTrimmedText("error.preferredName", 256),
        "dateOfBirth" -> DayMonthYear.validDayMonthYear("error.dateOfBirth", "error.dateOfBirthInFuture")(Some(minDob), maxDob),
        "address" -> Address.address,
        "postCode" -> text.verifying(validPostcode),
        "phone" -> of(phoneNumberFormatter),
        "alevel-d" -> optional(boolean).verifying("aleveld.required", _.isDefined),
        "alevel" -> optional(boolean).verifying("alevel.required", _.isDefined)
      )(Data.apply)(Data.unapply)
    )
  }

  case class Data(

    //personal details
    firstName: String,
    lastName: String,
    preferredName: String,
    dateOfBirth: DayMonthYear,

    //contact details
    address: Address,
    postCode: PostCode,
    phone: Option[PhoneNumber],

    aLevel: Option[Boolean],
    stemLevel: Option[Boolean]
  )

}
