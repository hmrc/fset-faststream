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

package controllers.forms

import controllers.BaseSpec
import forms.GeneralDetailsForm
import forms.GeneralDetailsForm.Data
import mappings.{ Address, DayMonthYear }
import org.joda.time.LocalDate

class GeneralDetailsFormSpec extends BaseSpec {

  "personal details form" should {
    "be valid when all values are correct" in new Fixture {
      val validForm = GeneralDetailsForm.form.bind(validFormValues)
      val expectedData = validFormData
      val actualData = validForm.get

      actualData mustBe expectedData
    }

    "be valid given a leap year 'date of birth'" in new Fixture {
      assertValidDateOfBirth(LocalDate.parse("1996-2-29"))
    }

    s"be valid given a 'date of birth' exactly ${GeneralDetailsForm.minAge} years ago" in new Fixture {
      assertValidDateOfBirth(ageReference.minusYears(GeneralDetailsForm.minAge))
    }

    s"be valid given a 'date of birth' over ${GeneralDetailsForm.minAge} years ago" in new Fixture {
      assertValidDateOfBirth(ageReference.minusYears(GeneralDetailsForm.minAge).minusDays(1))
    }

    "be invalid without a 'first name'" in new Fixture {
      assertFieldRequired("error.firstName", "firstName")
    }

    "be invalid without a 'last name'" in new Fixture {
      assertFieldRequired("error.lastName", "lastName")
    }

    "be invalid without a 'preferred name'" in new Fixture {
      assertFieldRequired("error.preferredName", "preferredName")
    }

    "be invalid without a 'day' for 'date of birth'" in new Fixture {
      assertFieldRequired("error.dateOfBirth", "dateOfBirth.day")
    }

    "be invalid without a 'month' for 'date of birth'" in new Fixture {
      assertFieldRequired("error.dateOfBirth", "dateOfBirth.month")
    }

    "be invalid without a 'year' for 'date of birth'" in new Fixture {
      assertFieldRequired("error.dateOfBirth", "dateOfBirth.year")
    }

    "be invalid given a 'date of birth' with non-integer values" in new Fixture {
      assertFormError("error.dateOfBirth", validFormValues + ("dateOfBirth.day" -> "a"))
      assertFormError("error.dateOfBirth", validFormValues + ("dateOfBirth.month" -> "a"))
      assertFormError("error.dateOfBirth", validFormValues + ("dateOfBirth.year" -> "a"))
    }

    "be invalid given a 'date of birth' with impossible dates" in new Fixture {
      assertFormError("error.dateOfBirth", validFormValues +
        ("dateOfBirth.day" -> "32") +
        ("dateOfBirth.month" -> "1") +
        ("dateOfBirth.year" -> "1988"))

      assertFormError("error.dateOfBirth", validFormValues +
        ("dateOfBirth.day" -> "29") +
        ("dateOfBirth.month" -> "2") +
        ("dateOfBirth.year" -> "2015"))

      assertFormError("error.dateOfBirth", validFormValues +
        ("dateOfBirth.day" -> "-9") +
        ("dateOfBirth.month" -> "2") +
        ("dateOfBirth.year" -> "2015"))
    }

    s"be invalid given a 'date of birth' before 01-01-1900" in new Fixture {
      assertValidDateOfBirth(new LocalDate(1900, 1, 1))
      assertInvalidDateOfBirth(new LocalDate(1899, 12, 25))
    }

    s"be invalid given a 'date of birth' less than ${GeneralDetailsForm.minAge} years ago" in new Fixture {
      assertInvalidDateOfBirth(ageReference.minusYears(GeneralDetailsForm.minAge).plusDays(1))
    }

    "be invalid given a 'date of birth' in the future" in new Fixture {
      assertInvalidDateOfBirth(now.plusDays(1))
    }

    "be invalid without a postcode" in new Fixture {
      assertFieldRequired("error.postcode.required", "postCode")
    }

    "be invalid with an incorrect postcode" in new Fixture {
      assertFormError("error.postcode.invalid", validFormValues + ("postCode" -> "BAD"))
    }

    "be invalid with a missing postcode" in new Fixture {
      assertFormError("error.postcode.required", validFormValues + ("postCode" -> ""))
    }

    "be invalid without an address" in new Fixture {
      assertFieldRequired("error.address.required", "address.line1")
    }

    "be invalid without any phone number" in new Fixture {
      assertFormError("error.phone.required", validFormValues + ("phone" -> ""))
    }

    "be invalid when you don't choose qualifications" in new Fixture {
      assertFormError("aleveld.required", validFormValues - "alevel-d")
      assertFormError("alevel.required", validFormValues - "alevel")
    }

  }

  trait Fixture {
    val year = 2015
    implicit val now: LocalDate = LocalDate.parse(s"$year-10-26")
    val ageReference = new LocalDate(year, 8, 31)

    val validFormData = Data(
      "Joseph",
      "Bloggs",
      "Joe",
      DayMonthYear("21", "1", "1988"),
      Address("line1", Some("line2"), Some("line3"), Some("line4")),
      "E14 9EL",
      Some("07912333333"),
      Some(false),
      Some(false)
    )

    val validFormValues = Map(
      "firstName" -> "Joseph",
      "lastName" -> "Bloggs",
      "preferredName" -> "Joe",
      "dateOfBirth.day" -> "21",
      "dateOfBirth.month" -> "1",
      "dateOfBirth.year" -> "1988",
      "address.line1" -> "line1",
      "address.line2" -> "line2",
      "address.line3" -> "line3",
      "address.line4" -> "line4",
      "postCode" -> "E14 9EL",
      "phone" -> "07912333333",
      "alevel-d" -> "false",
      "alevel" -> "false"
    )

    def assertFieldRequired(expectedError: String, fieldKey: String) =
      assertFormError(expectedError, validFormValues + (fieldKey -> ""))

    def assertFormError(expectedError: String, invalidFormValues: Map[String, String]) = {
      val invalidForm = GeneralDetailsForm.form.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.message) mustBe Seq(expectedError)
    }

    def assertValidDateOfBirth(validDate: LocalDate) = {
      val day = validDate.getDayOfMonth.toString
      val month = validDate.getMonthOfYear.toString
      val year = validDate.getYear.toString
      val validForm = GeneralDetailsForm.form.bind(validFormValues +
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
      assertFormError("error.dateOfBirth", validFormValues +
        ("dateOfBirth.day" -> day) +
        ("dateOfBirth.month" -> month) +
        ("dateOfBirth.year" -> year))
    }
  }
}
