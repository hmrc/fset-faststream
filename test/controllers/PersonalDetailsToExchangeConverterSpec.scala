/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers

import connectors.exchange.GeneralDetails
import forms.PersonalDetailsFormExamples._
import testkit.UnitSpec

class PersonalDetailsToExchangeConverterSpec extends UnitSpec {
  val converter = new PersonalDetailsToExchangeConverter {}

  "generalQuestions deatils to exchange converter" should {
    "convert uk address, and remove country" in {
      val result = converter.toExchange(ValidUKAddressForm.copy(country = Some("France")), "email@email.com", Some(true))

      result mustBe GeneralDetails(
        ValidUKAddressForm.firstName,
        ValidUKAddressForm.lastName,
        ValidUKAddressForm.preferredName,
        email = "email@email.com",
        ValidUKAddressForm.dateOfBirth,
        outsideUk = false,
        ValidUKAddressForm.address,
        ValidUKAddressForm.postCode,
        fsacIndicator = None,
        country = None,
        phone = ValidUKAddressForm.phone,
        civilServiceExperienceDetails = ValidUKAddressForm.civilServiceExperienceDetails,
        edipCompleted = None,
        edipYear = None,
        otherInternshipCompleted = None,
        otherInternshipName = None,
        otherInternshipYear = None,
        updateApplicationStatus = Some(true)
      )
    }

    "convert non uk address, and remove post code" in {
      val result = converter.toExchange(ValidNonUKAddressForm.copy(postCode = Some("1A 2BC")), "email@email.com", Some(true))

      result mustBe GeneralDetails(
        ValidNonUKAddressForm.firstName,
        ValidNonUKAddressForm.lastName,
        ValidNonUKAddressForm.preferredName,
        "email@email.com",
        ValidNonUKAddressForm.dateOfBirth,
        outsideUk = true,
        ValidNonUKAddressForm.address,
        postCode = None,
        fsacIndicator = None,
        country = ValidNonUKAddressForm.country,
        phone = ValidNonUKAddressForm.phone,
        civilServiceExperienceDetails = ValidNonUKAddressForm.civilServiceExperienceDetails,
        edipCompleted = None,
        edipYear = None,
        otherInternshipCompleted = None,
        otherInternshipName = None,
        otherInternshipYear = None,
        updateApplicationStatus = Some(true)
      )
    }
  }
}
