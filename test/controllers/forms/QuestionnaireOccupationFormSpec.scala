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
import forms.QuestionnaireOccupationInfoForm.{ Data, form }

class QuestionnaireOccupationFormSpec extends BaseSpec {

  "the occupation form" should {

    "be valid when all values are correct" in new Fixture {
      val validForm = form.bind(validFormValues)
      val expectedData = validFormData
      val actualData = validForm.get
      actualData mustBe expectedData
    }

    "fail when no employedParent" in new Fixture {
      assertFieldRequired("employedParent", "employedParent")
    }

    "fail when no employee" in new Fixture {
      assertFieldRequired("employee", "employee", "preferNotSay_employee")
    }

    "fail when no organizationSize" in new Fixture {
      assertFieldRequired("organizationSize", "organizationSize", "preferNotSay_organizationSize")
    }

    "fail when no supervise" in new Fixture {
      assertFieldRequired("supervise", "supervise", "preferNotSay_supervise")
    }

    "be valid when parents were unemployed" in new Fixture {
      val validFormUnemployed = form.bind(validFormValuesUnemployed)
      val expectedData = validFormDataUnemployed
      val actualData = validFormUnemployed.get
      actualData mustBe expectedData
    }

    "transform properly to a question list" in new Fixture {
      val questionList = validFormData.toQuestionnaire.questions
      questionList.size must be(4)
      questionList(0).answer.answer must be(Some("Some occupation"))
      questionList(1).answer.answer must be(Some("some employee"))
      questionList(2).answer.answer must be(Some("Org size"))
      questionList(3).answer.unknown must be(Some(true))
    }

  }

  trait Fixture {

    val validFormData = Data(
      "Employed",
      Some("Some occupation"),
      Some("some employee"), None,
      Some("Org size"), None,
      None, Some(true)
    )

    val validFormValues = Map(
      "employedParent" -> "Employed",
      "parentsOccupation" -> "Some occupation",
      "employee" -> "some employee",
      "preferNotSay_employee" -> "",
      "organizationSize" -> "Org size",
      "preferNotSay_organizationSize" -> "",
      "supervise" -> "",
      "preferNotSay_supervise" -> "true"
    )

    val validFormDataUnemployed = Data(
      "Unemployed", Some(""),
      None, None,
      None, None,
      None, None
    )

    val validFormValuesUnemployed = Map(
      "employedParent" -> "Unemployed",
      "parentsOccupation" -> "",
      "employee" -> "",
      "preferNotSay_employee" -> "",
      "organizationSize" -> "",
      "preferNotSay_organizationSize" -> "",
      "supervise" -> "",
      "preferNotSay_supervise" -> ""
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
