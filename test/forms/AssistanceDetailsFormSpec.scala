/*
 * Copyright 2018 HM Revenue & Customs
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

import forms.AssistanceDetailsForm.Data
import play.api.data.Form
import testkit.UnitWithAppSpec

class AssistanceDetailsFormSpec extends UnitWithAppSpec {

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

    "be invalid when venue adjustments are not selected for a fast-stream application" in new Fixture {
      assertFormError(Seq(
        "Tell us if you need any support when you visit any of our venues"
      ), AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsMap - "needsSupportAtVenue")
    }

    "be valid for an edip application" in new Fixture {
      AssistanceDetailsForm.form.bind(AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsEdipMap).hasErrors mustBe false
    }

    "be invalid edip application when adjustments are not selected for phone interview" in new Fixture {
      val invalidRequest = AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsEdipMap - "needsSupportForPhoneInterview"
      assertFormError(Seq(
        "Tell us if you need any support for your phone interview"
      ), invalidRequest)
    }

    "be valid for an sdip application" in new Fixture {
      AssistanceDetailsForm.form.bind(AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsSdipMap).hasErrors mustBe false
    }

    "be invalid sdip application when adjustments are not selected for phone interview" in new Fixture {
      val invalidRequest = AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsSdipMap - "needsSupportForPhoneInterview"
      assertFormError(Seq(
        "Tell us if you need any support for your phone interview"
      ), invalidRequest)
    }

    "be valid when application route is not selected" in new Fixture {
      val request = AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsMap - "applicationRoute"
      AssistanceDetailsForm.form.bind(request).hasErrors mustBe false
    }

    "be valid when application route and adjustments are not selected" in new Fixture {
      val invalidRequest = AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsMap - "applicationRoute" - "needsSupportForOnlineAssessment"
      assertFormError(Seq(
        "Tell us if you will need any support for your online tests"
      ), invalidRequest)
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
