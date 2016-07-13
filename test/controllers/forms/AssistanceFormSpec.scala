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
import forms.AssistanceForm
import forms.AssistanceForm.Data
import play.api.data.Form

class AssistanceFormSpec extends BaseSpec {

  "the assistance form" should {
    "be valid when the user selects no in the disability" in new Fixture {
      val (data, form) = noDisabilities
      form.get must be(data)
    }

    "be valid when the user selects yes in the disability and no in adjustment" in new Fixture {
      val (data, form) = noAdjustments
      form.get must be(data)
    }

    "be valid when the user fills the full form" in new Fixture {
      val (data, form) = fullForm
      form.get must be(data)
    }

    "be invalid when the form is invalid" in new Fixture {
      assertFormError(Seq(
        "Tell us about your disability",
        "Tell us if you wish to apply under the Guaranteed interview scheme",
        "needsAdjustment.chooseone"
      ), validFormValues - "needsAdjustment")
    }
  }

  trait Fixture {

    val noDisabilities = {
      val data = Data("No", Some(List("")), None, Some("No"), "No", None, None, None, None)

      (data, AssistanceForm.form.fill(data))
    }

    val noAdjustments = {
      val data = Data("Yes", Some(List("One of the disabilities")), None, None, "No", None, None, None, None)

      (data, AssistanceForm.form.fill(data))
    }

    val fullForm: (Data, Form[Data]) = {
      val data = Data("Yes", Some(List("One of the disabilities")), Some("some details"), None, "Yes", Some(List("other")), None, None, None)

      (data, AssistanceForm.form.fill(data))
    }

    def form(
      needsAssistance: String = "No",
      typeOfdisability: Option[List[String]] = None,
      detailsOfdisability: Option[String] = None,
      guaranteedInterview: Option[String] = None,
      needsAdjustment: String = "No",
      typeOfAdjustments: Option[List[String]],
      otherAdjustments: Option[String] = None
    ) = {

      val data = Data(needsAssistance, typeOfdisability, detailsOfdisability, guaranteedInterview, needsAdjustment,
        typeOfAdjustments, otherAdjustments, None, None)

      (data, AssistanceForm.form.fill(data))
    }

    val validFormValues = Map(
      "needsAssistance" -> "Yes",
      "typeOfdisability" -> "Epilepsy",
      "detailsOfdisability" -> "Some details for the disability",
      "needsAdjustment" -> "Yes",
      "extraTime" -> "true",
      "screenMagnification" -> "true",
      "printCopies" -> "true",
      "otherAdjustments" -> "Some other adjustements"
    )

    def assertFormError(expectedError: Seq[String], invalidFormValues: Map[String, String]) = {
      val invalidForm: Form[Data] = AssistanceForm.form.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.message) mustBe expectedError
    }
  }

}
