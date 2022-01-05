/*
 * Copyright 2022 HM Revenue & Customs
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

import forms.DiversityQuestionnaireForm.Data

class DiversityQuestionnaireFormSpec extends BaseFormSpec {

  "the diversity form" should {
    "be valid when all values are correct" in new Fixture {
      val validForm = formWrapper.bind(validFormValues)
      val expectedData = validFormData
      val actualData = validForm.get
      actualData mustBe expectedData
    }

    "fail when no gender is specified" in new Fixture {
      assertFieldRequired(expectedError = "gender", "gender")
    }

    "fail when gender is not a correct value" in new Fixture {
      assertFormError("gender", validFormValues ++ Seq("gender" -> "BOOM"))
    }

    "fail when other gender is too big" in new Fixture {
      assertFormError("other_gender", validFormValues ++ Seq("other_gender" -> "A" * (DiversityQuestionnaireForm.OtherMaxSize + 1)))
    }

    "fail when no sexOrientation is specified" in new Fixture {
      assertFieldRequired(expectedError = "sexOrientation", "sexOrientation")
    }

    "fail when sexOrientation is not a correct value" in new Fixture {
      assertFormError("sexOrientation", validFormValues ++ Seq("sexOrientation" -> "BOOM"))
    }

    "fail when other sexOrientation is too big" in new Fixture {
      assertFormError("sexOrientation", validFormValues ++ Seq("sexOrientation" -> "A" * (DiversityQuestionnaireForm.OtherMaxSize + 1)))
    }

    "be valid when ethnicity is specified and preferNotToSay is not selected" in new Fixture {
      val validForm = formWrapper.bind(validFormValues ++ Seq("ethnicity" -> "Irish", "preferNotSay_ethnicity" -> ""))
      val expectedData = Data(
        gender = "Man", otherGender = None,
        sexOrientation = "Other", otherSexOrientation = Some("details"),
        ethnicity = Some("Irish"), otherEthnicity = None, preferNotSayEthnicity = None,
        isEnglishFirstLanguage = "Yes"
      )
      val actualData = validForm.get
      actualData mustBe expectedData
    }

    "fail when no ethnicity is specified and preferNotToSay is not selected" in new Fixture {
      assertFieldRequired(expectedError = "ethnicity", validFormValues ++ Seq("preferNotSay_ethnicity" -> ""))
    }

    "fail when ethnicity is not a correct value and preferNotToSay is not selected" in new Fixture {
      assertFormError("ethnicity", validFormValues ++ Seq("ethnicity" -> "BOOM", "preferNotSay_ethnicity" -> ""))
    }

    "be valid when ethnicity is not a correct value and preferNotToSay is selected" in new Fixture {
      val validForm = formWrapper.bind(validFormValues ++ Seq("ethnicity" -> "BOOM"))
      val expectedData = Data(
        gender = "Man", otherGender = None,
        sexOrientation = "Other", otherSexOrientation = Some("details"),
        ethnicity = None, otherEthnicity = None, preferNotSayEthnicity = Some(true),
        isEnglishFirstLanguage = "Yes"
      )
      val actualData = validForm.get
      actualData mustBe expectedData
    }

    "fail when other ethnicity is too big" in new Fixture {
      assertFormError("other_ethnicity", validFormValues ++ Seq("other_ethnicity" -> "A" * (DiversityQuestionnaireForm.OtherMaxSize + 1)))
    }

    "fail when preferNotSay_ethnicity is not a correct value" in new Fixture {
      assertFormError("preferNotSay_ethnicity", validFormValues ++ Seq("preferNotSay_ethnicity" -> "BOOM"))
    }

    "fail when no language is specified" in new Fixture {
      assertFieldRequired(expectedError = "isEnglishFirstLanguage", "isEnglishFirstLanguage")
    }

    "fail when English language is not a correct value" in new Fixture {
      assertFormError("isEnglishFirstLanguage", validFormValues ++ Seq("isEnglishFirstLanguage" -> "BOOM"))
    }

    "transform properly to a question list" in new Fixture {
      val questionList = validFormData.exchange.questions
      questionList.size mustBe 4
      questionList(0).answer.answer mustBe Some("Man")
      questionList(1).answer.otherDetails mustBe Some("details")
      questionList(2).answer.unknown mustBe Some(true)
      questionList(3).answer.answer mustBe Some("Yes")
    }
  }

  trait Fixture {

    val validFormData = Data(
      gender = "Man", otherGender = None,
      sexOrientation = "Other", otherSexOrientation = Some("details"),
      ethnicity = None, otherEthnicity = None, preferNotSayEthnicity = Some(true),
      isEnglishFirstLanguage = "Yes"
    )

    val validFormValues = Map(
      "gender" -> "Man",
      "other_gender" -> "",

      "sexOrientation" -> "Other",
      "other_sexOrientation" -> "details",

      "ethnicity" -> "",
      "other_ethnicity" -> "",
      "preferNotSay_ethnicity" -> "true",

      "isEnglishFirstLanguage" -> "Yes"
    )

    val formWrapper = new DiversityQuestionnaireForm().form

    def assertFieldRequired(expectedError: String, formValues: Map[String, String]) =
      assertFormError(expectedError, formValues)

    def assertFieldRequired(expectedError: String, fieldKeysToClear: String*) =
      assertFormError(expectedError, validFormValues ++ fieldKeysToClear.map(k => k -> ""))

    def assertFormError(expectedKey: String, invalidFormValues: Map[String, String]) = {
      val invalidForm = formWrapper.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.key) mustBe Seq(expectedKey)
    }
  }
}
