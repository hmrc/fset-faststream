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
import forms.AssistanceDetailsForm
import forms.AssistanceDetailsForm.Data
import play.api.data.Form

class AssistanceDetailsFormSpec extends BaseSpec {

  "the assistance details form" should {
    "be valid when the user selects no in the disability" in new Fixture {
      val (data, form) = NoDisabilities
      form.get must be(data)
    }

    "be valid when the user selects yes in the disability and no in adjustment" in new Fixture {
      val (data, form) = NoAdjustments
      form.get must be(data)
    }

    "be valid when the user fills the full form" in new Fixture {
      val (data, form) = Full
      form.get must be(data)
    }

    "be invalid when the form is invalid" in new Fixture {
      assertFormError(Seq(
        "Tell us if you wish to apply under the Guaranteed interview scheme"
      ), AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsMap - "guaranteedInterview")
    }
  }

  trait Fixture {

    val NoDisabilities = (AssistanceDetailsFormExamples.NoDisabilitiesForm, AssistanceDetailsForm.form.fill(
      AssistanceDetailsFormExamples.NoDisabilitiesForm))

    val NoAdjustments = (AssistanceDetailsFormExamples.NoAdjustmentsForm, AssistanceDetailsForm.form.fill(
      AssistanceDetailsFormExamples.NoAdjustmentsForm))

    val Full = (AssistanceDetailsFormExamples.FullForm, AssistanceDetailsForm.form.fill(
      AssistanceDetailsFormExamples.FullForm))

    def assertFormError(expectedError: Seq[String], invalidFormValues: Map[String, String]) = {
      val invalidForm: Form[Data] = AssistanceDetailsForm.form.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.message) mustBe expectedError
    }
  }

}
