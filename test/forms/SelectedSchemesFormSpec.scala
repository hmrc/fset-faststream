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

import connectors.ReferenceDataExamples

class SelectedSchemesFormSpec extends BaseFormSpec {

  def selectedSchemesForm(isSdipFaststream: Boolean = false) =
    new SelectedSchemesForm(ReferenceDataExamples.Schemes.AllSchemes, isSdipFaststream).form

  "Selected Schemes form" should {
    "be valid when required values are supplied" in {
       val form = selectedSchemesForm().bind(Map("scheme_0" -> "Finance", "orderAgreed" -> "true",
         "eligible" -> "true"))
       form.hasErrors mustBe false
       form.hasGlobalErrors mustBe false
    }

    "be valid when multiple schemes are selected" in {
      val form = selectedSchemesForm().bind(Map(
        "scheme_0" -> "Finance",
        "scheme_1" -> "GovernmentEconomicsService",
        "scheme_2" -> "Commercial",
        "scheme_3" -> "DigitalAndTechnology",
        "orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be invalid when schemes are not supplied" in {
      val form = selectedSchemesForm().bind(Map("orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when invalid schemes are supplied" in {
      val form = selectedSchemesForm().bind(Map(
        "scheme_0" -> "InvalidScheme",
        "orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when filtered out scheme for 2021 campaign is supplied" in {
      val form = selectedSchemesForm().bind(Map(
        "scheme_0" -> "GovernmentCommunicationService",
        "scheme_1" -> "Commercial",
        "orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe true

      // A single invalid scheme will result in this error for the submission even if you do supply other valid schemes
      form.errors.head.message mustBe "schemes.required"
      form.hasGlobalErrors mustBe false
    }

    "be invalid when scheme order is not agreed by the candidate" in {
      val form = selectedSchemesForm().bind(Map("scheme_0" -> "Finance", "orderAgreed" -> "false",
        "eligible" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when eligibility criteria is not met by the candidate for selected scheme" in {
      val form = selectedSchemesForm().bind(Map("scheme_0" -> "Finance", "orderAgreed" -> "true",
        "eligible" -> "false"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when schemes exceed the maximum" in {
      val form = selectedSchemesForm().bind(Map(
        "scheme_0" -> "Finance",
        "scheme_1" -> "GovernmentEconomicsService",
        "scheme_2" -> "Commercial",
        "scheme_3" -> "DigitalAndTechnology",
        "scheme_4" -> "GovernmentDiplomaticService",
        "scheme_5" -> "GovernmentDiplomaticServiceEconomicsService",
        "orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when sdip faststream candidate has no faststream schemes" in {
      val form = selectedSchemesForm(isSdipFaststream = true).bind(Map(
        "scheme_0" -> "Sdip",
        "orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be valid when sdip faststream candidate has the max number of faststream schemes" in {
      val form = selectedSchemesForm(isSdipFaststream = true).bind(Map(
        "scheme_0" -> "Finance",
        "scheme_1" -> "GovernmentEconomicsService",
        "scheme_2" -> "Commercial",
        "scheme_3" -> "DigitalAndTechnology",
        "scheme_4" -> "Sdip",
        "orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be invalid when sdip faststream candidate exceeds the max number of schemes" in {
      val form = selectedSchemesForm(isSdipFaststream = true).bind(Map(
        "scheme_0" -> "Finance",
        "scheme_1" -> "GovernmentEconomicsService",
        "scheme_2" -> "Commercial",
        "scheme_3" -> "DigitalAndTechnology",
        "scheme_4" -> "GovernmentDiplomaticService",
        "scheme_5" -> "Sdip",
        "orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }
  }
}
