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
import forms.SchemeLocationPreferenceForm.{ Data, form, resetPreference, validateSchemeLocation }
import models.frameworks.{ Location, Preference, Region }
import org.junit.Assert._

class PreferenceFormSpec extends BaseSpec {

  "personal details form" should {
    "be valid when all values are correct" in new Fixture {
      val validForm = form.bind(validFormValues)
      val expectedData = validFormData
      val actualData = validForm.get
      actualData mustBe expectedData
    }

    "be valid when all values are correct - no second option" in new Fixture {
      val validForm = form.bind(validFormValues + ("secondScheme" -> ""))
      val expectedData = validFormData.copy(secondScheme = None)
      val actualData = validForm.get
      actualData mustBe expectedData
    }

    "fail when no location" in new Fixture {
      assertFieldRequired("location.required", "regionAndlocation")
    }

    "fail when no firstScheme" in new Fixture {
      assertFieldRequired("firstScheme.required", "firstScheme")
    }

    "be valid when the selection is available" in new Fixture {
      assertEquals(true, validateSchemeLocation(validFormData, validRegions).isEmpty)
    }

    "fail when the location is not available" in new Fixture {
      val errors = validateSchemeLocation(validFormData.copy(regionAndlocation = "London;Hackney"), validRegions)
      assertEquals("location.unavailable", errors.head)
    }

    "fail when the first selection is not available" in new Fixture {
      val errors = validateSchemeLocation(validFormData.copy(firstScheme = "Security"), validRegions)
      assertEquals("firstScheme.unavailable", errors.head)
    }

    "reset when the location is not available" in new Fixture {
      val pref: Preference = validFormData.copy(regionAndlocation = "London;Hackney")
      val errors = validateSchemeLocation(pref, validRegions)
      assertEquals(pref.copy(location = ""), resetPreference(pref, errors))
    }

    "reset when the first selection is not available" in new Fixture {
      val pref: Preference = validFormData.copy(firstScheme = "Security")
      val errors = validateSchemeLocation(pref, validRegions)
      assertEquals(pref.copy(firstFramework = "", secondFramework = None), resetPreference(pref, errors))
    }

    "reset when the second selection is not available" in new Fixture {
      val pref: Preference = validFormData.copy(secondScheme = Some("Security"))
      val errors = validateSchemeLocation(pref, validRegions)
      assertEquals(pref.copy(secondFramework = None), resetPreference(pref, errors))
    }

    "fail when the second selection is not available" in new Fixture {
      val errors = validateSchemeLocation(validFormData.copy(secondScheme = Some("Cyber")), validRegions)
      assertEquals("secondScheme.unavailable", errors.head)
    }

    "fail when the both selections are not available" in new Fixture {
      val errors = validateSchemeLocation(validFormData.copy(firstScheme = "Security", secondScheme = Some("Cyber")), validRegions)
      assertEquals("firstScheme.unavailable", errors.head)
      assertEquals("secondScheme.unavailable", errors.tail.head)
    }

    "fail when the both selections are the same" in new Fixture {
      val errors = validateSchemeLocation(validFormData.copy(firstScheme = "Security", secondScheme = Some("Security")), validRegions)
      assertEquals("secondScheme.duplicate", errors.head)
    }

  }

  trait Fixture {

    val validRegions = List(Region("London", locations = List(
      Location("London", frameworks = List("Business", "Commercial"))
    )))

    val validFormData = Data(
      "London;London",
      "Business",
      Some("Commercial")
    )

    val validFormValues = Map(
      "regionAndlocation" -> "London;London",
      "firstScheme" -> "Business",
      "secondScheme" -> "Commercial"
    )

    def assertFieldRequired(expectedError: String, fieldKey: String) =
      assertFormError(expectedError, validFormValues + (fieldKey -> ""))

    def assertFormError(expectedError: String, invalidFormValues: Map[String, String]) = {
      val invalidForm = form.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.message) mustBe Seq(expectedError)
    }
  }

}
