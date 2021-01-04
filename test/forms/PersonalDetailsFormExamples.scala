/*
 * Copyright 2021 HM Revenue & Customs
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
import models.ApplicationRoute
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
    "applicationRoute" -> ApplicationRoute.Sdip.toString,
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
    "edipCompleted" -> "true",
    "edipYear" -> "2020",
    "otherInternshipCompleted" -> "true",
    "otherInternshipName" -> "other name",
    "otherInternshipYear" -> "2020"
  )

  val SdipFsInProgressValidOutsideUKDetails = Map[String, String](
    "applicationRoute" -> ApplicationRoute.SdipFaststream.toString,
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
    "edipCompleted" -> "true",
    "edipYear" -> "2020",
    "otherInternshipCompleted" -> "true",
    "otherInternshipName" -> "other name",
    "otherInternshipYear" -> "2020"
  )

  val EdipInProgressValidOutsideUKDetails = Map[String, String](
    "applicationRoute" -> ApplicationRoute.Edip.toString,
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
    "otherInternshipCompleted" -> "true",
    "otherInternshipName" -> "other name",
    "otherInternshipYear" -> "2020"
  )

  val SdipSubmittedValidOutsideUKDetails = SdipInProgressValidOutsideUKDetails + ("applicationStatus" -> "SUBMITTED")

  val ValidUKAddressForm = PersonalDetailsForm.Data("firstName", "lastName", "preferredName", DayMonthYear("1", "2", birthYear),
    outsideUk = None, AddressExamples.FullAddress, Some("A1 2BC"), country = None, phone = Some("1234567890"),
    civilServiceExperienceDetails = toData(Some(CivilServiceExperienceDetails(applicable = false))), edipCompleted = None,
    edipYear = None, otherInternshipCompleted = None, otherInternshipName = None, otherInternshipYear = None)

  val ValidUKAddressWithoutCivilServiceDetailsForm = PersonalDetailsForm.Data("firstName", "lastName", "preferredName",
    DayMonthYear("1", "2", birthYear), outsideUk = None, AddressExamples.FullAddress, Some("A1 2BC"), country = None,
    phone = Some("1234567890"), civilServiceExperienceDetails = None, edipCompleted = None, edipYear = None,
    otherInternshipCompleted = None, otherInternshipName = None, otherInternshipYear = None)

  val ValidNonUKAddressForm = PersonalDetailsForm.Data("firstName", "lastName", "preferredName", DayMonthYear("1", "2", birthYear),
    outsideUk = Some(true), AddressExamples.FullAddress, postCode = None, country = Some("France"), phone = Some("1234567890"),
    civilServiceExperienceDetails = toData(Some(CivilServiceExperienceDetails(applicable = false))), edipCompleted = None, edipYear = None,
    otherInternshipCompleted = None, otherInternshipName = None, otherInternshipYear = None)

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
