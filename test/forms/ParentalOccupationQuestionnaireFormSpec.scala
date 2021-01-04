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

import forms.ParentalOccupationQuestionnaireForm.Data

class ParentalOccupationQuestionnaireFormSpec extends BaseFormSpec {

  val form = new ParentalOccupationQuestionnaireForm().form

  "the occupation form" should {

    "be valid when all values are correct" in new Fixture {
      val validForm = form.bind(validFormValues)
      val expectedData = validFormData
      val actualData = validForm.get
      actualData mustBe expectedData
    }

    "fail when no socio-economic background" in new Fixture {
      assertFieldRequired(expectedError = "socioEconomicBackground", "socioEconomicBackground")
    }

    "fail when no parents degree" in new Fixture {
      assertFieldRequired(expectedError = "parentsDegree", "parentsDegree")
    }

    "fail when no employedParent" in new Fixture {
      assertFieldRequired(expectedError = "employedParent", "employedParent")
    }

    "fail when no employee" in new Fixture {
      assertFieldRequired(expectedError = "employee", "employee")
    }

    "fail when no organizationSize" in new Fixture {
      assertFieldRequired(expectedError = "organizationSize", "organizationSize")
    }

    "fail when no supervise" in new Fixture {
      assertFieldRequired(expectedError = "supervise", "supervise")
    }

    "be valid when parents were unemployed" in new Fixture {
      val validFormUnemployed = form.bind(validFormValuesUnemployed)
      val expectedData = validFormDataUnemployed
      val actualData = validFormUnemployed.get
      actualData mustBe expectedData
    }

    "transform properly to a question list" in new Fixture {
      val questionList = validFormData.exchange.questions
      questionList.size must be(6)
      questionList(0).answer.answer must be(Some("Yes"))
      questionList(1).answer.answer must be(Some("Degree level qualification"))
      questionList(2).answer.answer must be(Some("Some occupation"))
      questionList(3).answer.answer must be(Some("Some employee"))
      questionList(4).answer.answer must be(Some("Org size"))
      questionList(5).answer.answer must be(Some("Yes"))
    }
  }

  trait Fixture {
    val validFormData = Data(
      "Yes",
      "Degree level qualification",
      "Employed",
      Some("Some occupation"),
      Some("Some employee"),
      Some("Org size"),
      Some("Yes")
    )

    val validFormValues = Map(
      "socioEconomicBackground" -> "Yes",
      "parentsDegree" -> "Degree level qualification",
      "employedParent" -> "Employed",
      "parentsOccupation" -> "Some occupation",
      "employee" -> "Some employee",
      "organizationSize" -> "Org size",
      "supervise" -> "Yes"
    )

    val validFormDataUnemployed = Data(
      "No",
      "No formal qualifications",
      "Unemployed",
      None,
      None,
      None,
      None
    )

    val validFormValuesUnemployed = Map(
      "socioEconomicBackground" -> "No",
      "parentsDegree" -> "No formal qualifications",
      "employedParent" -> "Unemployed",
      "parentsOccupation" -> "",
      "employee" -> "",
      "organizationSize" -> "",
      "supervise" -> ""
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
