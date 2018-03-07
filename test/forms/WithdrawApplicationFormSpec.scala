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

import controllers.UnitSpec
import testkit.UnitWithAppSpec
import forms.WithdrawApplicationForm.Data
import play.api.data.Form

class WithdrawApplicationFormSpec extends UnitWithAppSpec {

  "the withdraw application form" should {
    "be valid when the user selects I want to withdraw and provide a reason (no other reason)" in new Fixture {
      val (data, form) = Valid
      form.get must be(data)
    }

    "be valid when the user selects I want to withdraw and provide a another reason and more info" in new Fixture {
      val (data, form) = OtherReasonValid
      form.get must be(data)
    }

    "be invalid when the user selects I want to withdraw and provide no reason" in new Fixture {
      assertFormError(Seq(
        "Select a reason for withdrawing your application"
      ), WithdrawApplicationFormExamples.OtherReasonInvalidNoReasonMap)
    }

    "be invalid when the user selects I want to withdraw and select other reason and provide no more info" in new Fixture {
      assertFormError(Seq(
        "Tell us why you're withdrawing your application"
      ), WithdrawApplicationFormExamples.OtherReasonInvalidNoOtherReasonMoreInfoMap)
    }
  }

  trait Fixture {

    val Valid = (WithdrawApplicationFormExamples.ValidForm, WithdrawApplicationForm.form.fill(
      WithdrawApplicationFormExamples.ValidForm))

    val OtherReasonValid = (WithdrawApplicationFormExamples.OtherReasonValidForm, WithdrawApplicationForm.form.fill(
      WithdrawApplicationFormExamples.OtherReasonValidForm))

    val OtherReasonInvalidNoReason = (WithdrawApplicationFormExamples.OtherReasonInvalidNoReasonForm, WithdrawApplicationForm.form.fill(
      WithdrawApplicationFormExamples.OtherReasonInvalidNoReasonForm))

    val OtherReasonInvalidNoOtherReasonMoreInfo = (WithdrawApplicationFormExamples.OtherReasonInvalidNoOtherReasonMoreInfoForm,
      WithdrawApplicationForm.form.fill(WithdrawApplicationFormExamples.OtherReasonInvalidNoOtherReasonMoreInfoForm))

    def assertFormError(expectedError: Seq[String], invalidFormValues: Map[String, String]) = {
      val invalidForm: Form[Data] = WithdrawApplicationForm.form.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.message) mustBe expectedError
    }
  }
}
