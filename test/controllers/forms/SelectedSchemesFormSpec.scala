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

import org.scalatestplus.play.PlaySpec
import forms.SelectedSchemesForm.{form => selectedSchemesForm}


class SelectedSchemesFormSpec extends PlaySpec {

  "Selected Schemes form" should {
    "be valid when required values are supplied" in {
       val form = selectedSchemesForm.bind(Map("scheme_0" -> "Finance", "orderAgreed" -> "true",
         "eligible" -> "true", "alternatives" -> "false"))
       form.hasErrors mustBe false
       form.hasGlobalErrors mustBe false
    }

    "be invalid when schemes are not supplied" in {
      val form = selectedSchemesForm.bind(Map("orderAgreed" -> "true",
        "eligible" -> "true", "alternatives" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when more than 5 schemes are supplied" in {
      val form = selectedSchemesForm.bind(Map(
        "scheme_0" -> "Finance", "scheme_1" -> "CentralDepartments", "scheme_2" -> "Commercial",
        "scheme_3" -> "DigitalAndTechnology", "scheme_4" -> "DiplomaticService",
        "scheme_5" -> "GovernmentOperationalResearchService",
        "orderAgreed" -> "true",
        "eligible" -> "true", "alternatives" -> "false"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when invalid schemes are supplied" in {
      val form = selectedSchemesForm.bind(Map(
        "scheme_0" -> "InvalidScheme",
        "orderAgreed" -> "true",
        "eligible" -> "true", "alternatives" -> "false"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when non boolean string is supplied for alternatives" in {
      val form = selectedSchemesForm.bind(Map(
        "scheme_0" -> "Finance",
        "orderAgreed" -> "true",
        "eligible" -> "true", "alternatives" -> "non-boolean"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when scheme order is not agreed by the candidate" in {
      val form = selectedSchemesForm.bind(Map("scheme_0" -> "Finance", "orderAgreed" -> "false",
        "eligible" -> "true", "alternatives" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when eligibility criteria is not met by the candidate for selected scheme" in {
      val form = selectedSchemesForm.bind(Map("scheme_0" -> "Finance", "orderAgreed" -> "true",
        "eligible" -> "false", "alternatives" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }
  }

}
