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

import forms.AssistanceDetailsForm.Data
import play.api.data.{ Form, FormError }
import testkit.UnitWithAppSpec

class AssistanceDetailsFormSpec extends UnitWithAppSpec {

  "the assistance details form" should {
    "be valid when the fast stream candidate indicates they have no disabilities and need no support at all" in new Fixture {
      val form = AssistanceDetailsForm.form.bind(Map(
        "hasDisability" -> "No",
        "needsSupportForOnlineAssessment" -> "No",
        "needsSupportAtVenue" -> "No"

      ))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be valid when the fast stream candidate fills the full form" in new Fixture {
      val form = AssistanceDetailsForm.form.bind(Map(
        "hasDisability" -> "No",
        "disabilityImpact" -> "No",
        "disabilityCategories[0]" -> AssistanceDetailsForm.disabilityCategoriesList.head,
        "disabilityCategories[1]" -> AssistanceDetailsForm.other,
        "otherDisabilityDescription" -> "Some other description",
        "guaranteedInterview" -> "Yes",
        "needsSupportForOnlineAssessment" -> "Yes",
        "needsSupportForOnlineAssessmentDescription" -> "Some online test adjustments",
        "needsSupportAtVenue" -> "Yes",
        "needsSupportAtVenueDescription" -> "Some fsac adjustments"
      ))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be invalid when the fast stream candidate submits an invalid value for disabilityImpact question" in new Fixture {
      val form = AssistanceDetailsForm.form.bind(Map(
        "hasDisability" -> "Yes",
        "disabilityImpact" -> "BOOM",
        "disabilityCategories[0]" -> AssistanceDetailsForm.disabilityCategoriesList.head,
        "guaranteedInterview" -> "Yes",
        "needsSupportForOnlineAssessment" -> "Yes",
        "needsSupportForOnlineAssessmentDescription" -> "Some online test adjustments",
        "needsSupportAtVenue" -> "Yes",
        "needsSupportAtVenueDescription" -> "Some fsac adjustments"
      ))
      form.hasErrors mustBe true
      val expectedFormErrors = Seq(FormError(key = "disabilityImpact", message = disabilityImpactErrorMsg))
      form.errors mustBe expectedFormErrors
      form.hasGlobalErrors mustBe false
    }

    "be invalid when the fast stream candidate submits an invalid disability category" in new Fixture {
      val form = AssistanceDetailsForm.form.bind(Map(
        "hasDisability" -> "Yes",
        "disabilityImpact" -> "No",
        "disabilityCategories[0]" -> "BOOM",
        "guaranteedInterview" -> "Yes",
        "needsSupportForOnlineAssessment" -> "Yes",
        "needsSupportForOnlineAssessmentDescription" -> "Some online test adjustments",
        "needsSupportAtVenue" -> "Yes",
        "needsSupportAtVenueDescription" -> "Some fsac adjustments"
      ))
      form.hasErrors mustBe true
      val expectedFormErrors = Seq(FormError(key = "disabilityCategories", message = disabilityCategoriesErrorMsg))
      form.errors mustBe expectedFormErrors
      form.hasGlobalErrors mustBe false
    }

    "be invalid when the fast stream candidate indicates they have disabilities but does not fill in the other fields" in new Fixture {
      val form = AssistanceDetailsForm.form.bind(Map(
        "hasDisability" -> "Yes",
        "needsSupportForOnlineAssessment" -> "Yes",
        "needsSupportForOnlineAssessmentDescription" -> "Some online test adjustments",
        "needsSupportAtVenue" -> "Yes",
        "needsSupportAtVenueDescription" -> "Some fsac adjustments"
      ))
      form.hasErrors mustBe true

      val expectedFormErrors = Seq(
        FormError(key = "disabilityImpact", message = disabilityImpactErrorMsg),
        FormError(key = "disabilityCategories", message = disabilityCategoriesErrorMsg),
        FormError(key = "guaranteedInterview", message = gisErrorMsg)
      )
      form.errors mustBe expectedFormErrors
      form.hasGlobalErrors mustBe false
    }

    "be invalid when the fast stream candidate indicates they have disabilities, chooses other disability category but " +
      "does not fill in the description" in new Fixture {
      val form = AssistanceDetailsForm.form.bind(Map(
        "hasDisability" -> "Yes",
        "disabilityImpact" -> "No",
        "disabilityCategories[0]" -> AssistanceDetailsForm.other,
        "guaranteedInterview" -> "Yes",
        "needsSupportForOnlineAssessment" -> "Yes",
        "needsSupportForOnlineAssessmentDescription" -> "Some online test adjustments",
        "needsSupportAtVenue" -> "Yes",
        "needsSupportAtVenueDescription" -> "Some fsac adjustments"
      ))
      form.hasErrors mustBe true
      val expectedFormErrors = Seq(FormError(key = "otherDisabilityDescription", message = "You must provide a disability description"))
      form.errors mustBe expectedFormErrors
      form.hasGlobalErrors mustBe false
    }

    "be invalid when the fast stream candidate indicates they have disabilities, chooses other disability category and " +
      "fills in the description with too much text" in new Fixture {
      val form = AssistanceDetailsForm.form.bind(Map(
        "hasDisability" -> "Yes",
        "disabilityImpact" -> "No",
        "disabilityCategories[0]" -> AssistanceDetailsForm.other,
        "otherDisabilityDescription" -> "A" * (AssistanceDetailsForm.otherDisabilityCategoryMaxSize + 1),
        "guaranteedInterview" -> "Yes",
        "needsSupportForOnlineAssessment" -> "Yes",
        "needsSupportForOnlineAssessmentDescription" -> "Some online test adjustments",
        "needsSupportAtVenue" -> "Yes",
        "needsSupportAtVenueDescription" -> "Some fsac adjustments"
      ))
      form.hasErrors mustBe true
      val expectedFormErrors = Seq(FormError(key = "otherDisabilityDescription",
        message = s"The disability description must not exceed ${AssistanceDetailsForm.otherDisabilityCategoryMaxSize} characters"))
      form.errors mustBe expectedFormErrors
      form.hasGlobalErrors mustBe false
    }

    "be invalid when the fast stream candidate submits 'prefer not to say' disability along with a 2nd option" in new Fixture {
      val form = AssistanceDetailsForm.form.bind(Map(
        "hasDisability" -> "Yes",
        "disabilityImpact" -> "No",
        "disabilityCategories[0]" -> AssistanceDetailsForm.preferNotToSay,
        "disabilityCategories[1]" -> AssistanceDetailsForm.disabilityCategoriesList.head,
        "guaranteedInterview" -> "Yes",
        "needsSupportForOnlineAssessment" -> "No",
        "needsSupportAtVenue" -> "No"
      ))
      form.hasErrors mustBe true
      val expectedFormErrors = Seq(FormError(key = "disabilityCategories", message = disabilityCategoriesErrorMsg))
      form.errors mustBe expectedFormErrors
      form.hasGlobalErrors mustBe false
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
      assertFormError(Seq("Tell us if you need any support for your phone interview"), invalidRequest)
    }

    "be valid for an sdip application" in new Fixture {
      AssistanceDetailsForm.form.bind(AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsSdipMap).hasErrors mustBe false
    }

    "be invalid sdip application when adjustments are not selected for phone interview" in new Fixture {
      val invalidRequest = AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsSdipMap - "needsSupportForPhoneInterview"
      assertFormError(Seq("Tell us if you need any support for your phone interview"), invalidRequest)
    }

    "be valid when application route is not selected" in new Fixture {
      val request = AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsMap - "applicationRoute"
      AssistanceDetailsForm.form.bind(request).hasErrors mustBe false
    }

    "be valid when application route and adjustments are not selected" in new Fixture {
      val invalidRequest = AssistanceDetailsFormExamples.DisabilityGisAndAdjustmentsMap - "applicationRoute" - "needsSupportForOnlineAssessment"
      assertFormError(Seq("Tell us if you will need any support for your online tests"), invalidRequest)
    }
  }

  trait Fixture {
    val disabilityImpactErrorMsg = "You must provide a valid disability impact"
    val disabilityCategoriesErrorMsg = "Choose a valid disability category"
    val gisErrorMsg = "Tell us if you wish to apply under the Disability Confident scheme"

    def assertFormError(expectedError: Seq[String], invalidFormValues: Map[String, String]) = {
      val invalidForm: Form[Data] = AssistanceDetailsForm.form.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.message) mustBe expectedError
    }
  }
}
