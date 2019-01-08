/*
 * Copyright 2019 HM Revenue & Customs
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
import forms.DiversityQuestionnaireForm.{ Data, form }
import testkit.UnitWithAppSpec

class DiversityQuestionnaireFormSpec extends UnitWithAppSpec {

  "the diversity form" should {
    "be valid when all values are correct" in new Fixture {
      val validForm = form.bind(validFormValues)
      val expectedData = validFormData
      val actualData = validForm.get
      actualData mustBe expectedData
    }

    "fail when no gender" in new Fixture {
      assertFieldRequired(expectedError = "gender", "gender")
    }

    "fail when no orientation" in new Fixture {
      assertFieldRequired(expectedError = "sexOrientation", "sexOrientation")
    }

    "fail when no ethnicity" in new Fixture {
      assertFieldRequired(expectedError = "ethnicity", "other_ethnicity", "preferNotSay_ethnicity")
    }

    "transform properly to a question list" in new Fixture {
      val questionList = validFormData.exchange.questions
      questionList.size must be(3)
      questionList(0).answer.answer must be(Some("Male"))
      questionList(1).answer.otherDetails must be(Some("details"))
      questionList(2).answer.unknown must be(Some(true))
    }
  }

  trait Fixture {

    val validFormData = Data(
      "Male", None,
      "Other", Some("details"),
      None, None, Some(true)
    )

    val validFormValues = Map(
      "gender" -> "Male",
      "other_gender" -> "",

      "sexOrientation" -> "Other",
      "other_sexOrientation" -> "details",

      "ethnicity" -> "",
      "other_ethnicity" -> "",
      "preferNotSay_ethnicity" -> "true"
    )

    def assertFieldRequired(expectedError: String, fieldKey: String*) =
      assertFormError(expectedError, validFormValues ++ fieldKey.map(k => k -> ""))

    def assertFormError(expectedKey: String, invalidFormValues: Map[String, String]) = {
      val invalidForm = form.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.key) mustBe Seq(expectedKey)
    }
  }
}
