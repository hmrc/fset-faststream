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

import connectors.exchange.CivilServiceExperienceDetails
import connectors.exchange.CivilServiceExperienceDetails.toData
import mappings.{ AddressExamples, DayMonthYear }
import org.joda.time.{ DateTime, LocalDate }

object PersonalDetailsFormExamples {
  val ValidOutsideUKDetails = Map[String, String](
    "firstName" -> "John",
    "lastName" -> "Biggs",
    "preferredName" -> "john",
    "dateOfBirth.day" -> "1",
    "dateOfBirth.month" -> "2",
    "dateOfBirth.year" -> "1990",
    "address.line1" -> "Line 1",
    "outsideUk" -> "true",
    "country" -> "France",
    "phone" -> "123456789",
    "civilServiceExperienceDetails.applicable" -> "false"
  )

  val InvalidUKAddressWithoutPostCode = ValidOutsideUKDetails - "outsideUk"

  val ValidUKAddress = InvalidUKAddressWithoutPostCode + ("postCode" -> "A1 2BC")

  val InvalidAddressDoBInFuture = ValidUKAddress + ("dateOfBirth.year" -> yearInTheFuture)

  val InsideUKMandatoryFieldsFaststream = List(
    "firstName",
    "lastName",
    "preferredName",
    "dateOfBirth.day",
    "dateOfBirth.month",
    "dateOfBirth.year",
    "address.line1",
    "postCode",
    "phone",
    "civilServiceExperienceDetails.applicable"
  )

  val SdipInProgressValidOutsideUKDetails = Map[String, String](
    "applicationRoute" -> "Sdip",
    "applicationStatus" -> "IN_PROGRESS",
    "firstName" -> "John",
    "lastName" -> "Biggs",
    "preferredName" -> "john",
    "dateOfBirth.day" -> "1",
    "dateOfBirth.month" -> "2",
    "dateOfBirth.year" -> "1990",
    "address.line1" -> "Line 1",
    "outsideUk" -> "true",
    "country" -> "France",
    "phone" -> "123456789",
    "civilServiceExperienceDetails.applicable" -> "false",
    "edipCompleted" -> "true"
  )

  val SdipSubmittedValidOutsideUKDetails = SdipInProgressValidOutsideUKDetails + ("applicationStatus" -> "SUBMITTED")

  val ValidUKAddressForm = PersonalDetailsForm.Data("firstName", "lastName", "preferredName", DayMonthYear("1", "2", birthYear),
    outsideUk = None, AddressExamples.FullAddress, Some("A1 2BC"), None, Some("1234567890"),
    toData(Some(CivilServiceExperienceDetails(applicable = false))), None)

  val ValidUKAddressWithoutCivilServiceDetailsForm = PersonalDetailsForm.Data("firstName", "lastName", "preferredName",
    DayMonthYear("1", "2", birthYear), outsideUk = None, AddressExamples.FullAddress, Some("A1 2BC"), None, Some("1234567890"),
    None, None)

  val ValidNonUKAddressForm = PersonalDetailsForm.Data("firstName", "lastName", "preferredName", DayMonthYear("1", "2", birthYear),
    outsideUk = Some(true), AddressExamples.FullAddress, None, Some("France"), Some("1234567890"),
    toData(Some(CivilServiceExperienceDetails(applicable = false))), None)

  val ValidFormUrlEncodedBody = Seq(
    "firstName" -> ValidUKAddressForm.firstName,
    "lastName" -> ValidUKAddressForm.lastName,
    "preferredName" -> ValidUKAddressForm.preferredName,
    "dateOfBirth.day" -> ValidUKAddressForm.dateOfBirth.day,
    "dateOfBirth.month" -> ValidUKAddressForm.dateOfBirth.month,
    "dateOfBirth.year" -> ValidUKAddressForm.dateOfBirth.year,
    "address.line1" -> ValidUKAddressForm.address.line1,
    "address.line2" -> ValidUKAddressForm.address.line2.getOrElse(""),
    "address.line3" -> ValidUKAddressForm.address.line3.getOrElse(""),
    "address.line4" -> ValidUKAddressForm.address.line4.getOrElse(""),
    "postCode" -> ValidUKAddressForm.postCode.getOrElse(""),
    "phone" -> ValidUKAddressForm.phone.map(_.toString).getOrElse(""),
    "civilServiceExperienceDetails.applicable" -> ValidUKAddressForm.civilServiceExperienceDetails.get.applicable.toString
  )

  private def yearInTheFuture = DateTime.now().plusYears(2).year().get().toString

  def birthYear = LocalDate.now.minusYears(18).year().get().toString

  def now = LocalDate.now
}
