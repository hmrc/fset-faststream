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

import forms.PersonalDetailsFormExamples._
import org.joda.time.LocalDate
import testkit.UnitWithAppSpec

class PersonalDetailsFormSpec extends UnitWithAppSpec {
  implicit val now = LocalDate.now

  import PersonalDetailsForm.{ form => personalDetailsForm }

  "Personal Details form" should {
    "be invalid for missing mandatory fields" in {
      val form = personalDetailsForm.bind(Map[String, String]())
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
      form.errors.map(_.key) mustBe InsideUKMandatoryFieldsFaststream
    }

    "be successful for outside UK address without post code, but with country" in {
      val form = personalDetailsForm.bind(ValidOutsideUKDetails)
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be invalid for UK address without post code" in {
      val form = personalDetailsForm.bind(InvalidUKAddressWithoutPostCode)
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
      form.errors.flatMap(_.messages) mustBe List("error.postcode.required")
    }

    "be valid for UK address with post code" in {
      val form = personalDetailsForm.bind(ValidUKAddress)
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be invalid for date of birth in the future" in {
      val form = personalDetailsForm.bind(InvalidAddressDoBInFuture)
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
      form.errors.flatMap(_.messages) mustBe List("error.dateOfBirthInFuture")
    }

    "be valid given a leap year 'date of birth'" in {
      assertValidDateOfBirth(LocalDate.parse("1996-2-29"))
    }

    "be valid given a 'date of birth' exactly 16 years ago" in {
      assertValidDateOfBirth(ageReference.minusYears(16))
    }

    "be valid given a 'date of birth' over 16 years ago" in {
      assertValidDateOfBirth(ageReference.minusYears(16).minusDays(1))
    }

    "be invalid given a 'date of birth' with non-integer values" in {
      assertFormError("error.dateOfBirth", ValidUKAddress + ("dateOfBirth.day" -> "a"))
      assertFormError("error.dateOfBirth", ValidUKAddress + ("dateOfBirth.month" -> "a"))
      assertFormError("error.dateOfBirth", ValidUKAddress + ("dateOfBirth.year" -> "a"))
    }

    "be invalid given a 'date of birth' with impossible dates" in {
      assertFormError("error.dateOfBirth", ValidUKAddress +
        ("dateOfBirth.day" -> "32") +
        ("dateOfBirth.month" -> "1") +
        ("dateOfBirth.year" -> "1988"))

      assertFormError("error.dateOfBirth", ValidUKAddress +
        ("dateOfBirth.day" -> "29") +
        ("dateOfBirth.month" -> "2") +
        ("dateOfBirth.year" -> "2015"))

      assertFormError("error.dateOfBirth", ValidUKAddress +
        ("dateOfBirth.day" -> "-9") +
        ("dateOfBirth.month" -> "2") +
        ("dateOfBirth.year" -> "2015"))
    }

    s"be invalid given a 'date of birth' before 01-01-1900" in {
      assertValidDateOfBirth(new LocalDate(1900, 1, 1))
      assertInvalidDateOfBirth(new LocalDate(1899, 12, 25))
    }

    s"be invalid given a 'date of birth' less than 16 years ago" in {
      assertInvalidDateOfBirth(ageReference.minusYears(16).plusDays(1))
    }

    "be invalid given a 'date of birth' in the future" in {
      assertInvalidDateOfBirth(now.plusDays(1))
    }

    "be invalid without a postcode" in {
      assertFieldRequired("error.postcode.required", "postCode")
    }

    "be invalid with an incorrect postcode" in {
      assertFormError("error.postcode.invalid", ValidUKAddress + ("postCode" -> "BAD"))
    }

    "be invalid with a missing postcode" in {
      assertFormError("error.postcode.required", ValidUKAddress + ("postCode" -> ""))
    }

    "be invalid without an address" in {
      assertFieldRequired("error.address.required", "address.line1")
    }

    "be invalid without any phone number" in {
      assertFormError("error.phone.required", ValidUKAddress + ("phone" -> ""))
    }

    // sdip
    "be valid with all mandatory fields for an sdip candidate" in {
      val form = personalDetailsForm.bind(SdipInProgressValidOutsideUKDetails)
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be valid with all mandatory fields except edipCompleted question for submitted sdip candidate" in {
      val form = personalDetailsForm.bind(SdipSubmittedValidOutsideUKDetails - "edipCompleted")
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be invalid with all mandatory fields except edipCompleted question for in progress sdip candidate" in {
      assertFormError("Tell us if you have completed EDIP", SdipInProgressValidOutsideUKDetails - "edipCompleted")
    }

    "be invalid with all mandatory fields except edipCompleted and otherInternshipCompleted for in progress sdip candidate" in {
      assertFormErrors(Seq("Tell us if you have completed EDIP", "Tell us if you completed another internship programme"),
        SdipInProgressValidOutsideUKDetails - "edipCompleted" - "otherInternshipCompleted")
    }

    "be invalid when not answering 2nd level questions for in progress sdip candidate" in {
      assertFormErrors(Seq("error.edipYear.required", "error.otherInternshipName.required", "error.otherInternshipYear.required"),
        SdipInProgressValidOutsideUKDetails - "edipYear" - "otherInternshipName" - "otherInternshipYear")
    }

    // sdip faststream
    "be valid with all mandatory fields for an sdip fs candidate" in {
      val form = personalDetailsForm.bind(SdipFsInProgressValidOutsideUKDetails)
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be invalid with all mandatory fields except edipCompleted question for in progress sdip fs candidate" in {
      assertFormError("Tell us if you have completed EDIP", SdipFsInProgressValidOutsideUKDetails - "edipCompleted")
    }

    "be invalid with all mandatory fields except edipCompleted and otherInternshipCompleted for in progress sdip fs candidate" in {
      assertFormErrors(Seq("Tell us if you have completed EDIP", "Tell us if you completed another internship programme"),
        SdipFsInProgressValidOutsideUKDetails - "edipCompleted" - "otherInternshipCompleted")
    }

    "be invalid when not answering 2nd level questions for in progress sdip fs candidate" in {
      assertFormErrors(Seq("error.edipYear.required", "error.otherInternshipName.required", "error.otherInternshipYear.required"),
        SdipFsInProgressValidOutsideUKDetails - "edipYear" - "otherInternshipName" - "otherInternshipYear")
    }

    // edip
    "be valid with all mandatory fields for an edip candidate" in {
      val form = personalDetailsForm.bind(EdipInProgressValidOutsideUKDetails)
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be invalid with all mandatory fields except otherInternshipCompleted question for in progress edip candidate" in {
      assertFormError("Tell us if you completed an internship programme",
        EdipInProgressValidOutsideUKDetails - "otherInternshipCompleted")
    }

    "be invalid when not answering 2nd level questions for in progress edip candidate" in {
      assertFormErrors(Seq("error.otherInternshipName.required", "error.otherInternshipYear.required"),
        EdipInProgressValidOutsideUKDetails - "otherInternshipName" - "otherInternshipYear")
    }
  }

  def assertFieldRequired(expectedError: String, fieldKey: String) =
    assertFormError(expectedError, ValidUKAddress + (fieldKey -> ""))

  def assertFormError(expectedError: String, invalidFormValues: Map[String, String]) = {
    assertFormErrors(Seq(expectedError), invalidFormValues)
  }

  def assertFormErrors(expectedErrors: Seq[String], invalidFormValues: Map[String, String]) = {
    val invalidForm = PersonalDetailsForm.form.bind(invalidFormValues)
    invalidForm.hasErrors mustBe true
    val actualErrors = invalidForm.errors.map( _.message )
    actualErrors mustBe expectedErrors
  }

  def assertValidDateOfBirth(validDate: LocalDate) = {
    val day = validDate.getDayOfMonth.toString
    val month = validDate.getMonthOfYear.toString
    val year = validDate.getYear.toString
    val validForm = PersonalDetailsForm.form.bind(ValidUKAddress +
      ("dateOfBirth.day" -> day) +
      ("dateOfBirth.month" -> month) +
      ("dateOfBirth.year" -> year))
    validForm.hasErrors mustBe false
    val actualData = validForm.get
    actualData.dateOfBirth.day mustBe day
    actualData.dateOfBirth.month mustBe month
    actualData.dateOfBirth.year mustBe year
  }

  def assertInvalidDateOfBirth(invalidDate: LocalDate) = {
    val day = invalidDate.getDayOfMonth.toString
    val month = invalidDate.monthOfYear.toString
    val year = invalidDate.getYear.toString
    assertFormError("error.dateOfBirth", ValidUKAddress +
      ("dateOfBirth.day" -> day) +
      ("dateOfBirth.month" -> month) +
      ("dateOfBirth.year" -> year))
  }

  def ageReference(implicit now: LocalDate) = new LocalDate(now.getYear, 8, 31)
}
