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
import controllers.forms.EducationQuestionnaireFormExamples._
import forms.EducationQuestionnaireForm

class EducationQuestionnaireFormSpec extends BaseSpec {

  "the education form" should {
    "be valid when all values are correct" in new Fixture {
      val (data, form) = FullValid
      form.get must be(data)
    }

    "be valid when all possible values are <<I don't know/ prefer not to say>>" in new Fixture {
      val (data, form) = AllPreferNotToSayValid
      form.get must be(data)
    }

    "be valid when no lived in UK" in new Fixture {
      val (data, form) = NoUkValid
      form.get must be(data)
    }

    "be valid when no civil servant in UK" in new Fixture {
      val (data, form) = NoCivilServantValid
      form.get must be(data)
    }

    "be valid when no lived in UK And civil servant in UK" in new Fixture {
      val (data, form) = NoUkLivedAndNoCivilServantValid
      form.get must be(data)
    }

    "fail when all values are correct but no lived in UK" in new Fixture {
      assertFieldRequired(FullValidFormMap, "liveInUKBetween14and18", "liveInUKBetween14and18")
    }

    "fail when all values are correct but no postcodeQ" in new Fixture {
      assertFieldRequired(FullValidFormMap, "postcodeQ", "postcodeQ")
    }

    "fail when all values are correct but no schoolName14to16" in new Fixture {
      assertFieldRequired(FullValidFormMap, "schoolName14to16", "schoolName14to16")
    }

    "fail when all values are correct but no schoolName16to18" in new Fixture {
      assertFieldRequired(FullValidFormMap, "schoolName16to18", "schoolName16to18")
    }

    "fail when all values are correct but no freeSchoolMeals" in new Fixture {
      assertFieldRequired(FullValidFormMap, "freeSchoolMeals", "freeSchoolMeals")
    }

    "fail when all values are correct but no university" in new Fixture {
      assertFieldRequired(FullValidFormMap, "university", "university")
    }

    "fail when all values are correct but no universityDegreeCategory" in new Fixture {
      assertFieldRequired(FullValidFormMap, "universityDegreeCategory", "universityDegreeCategory")
    }

    "fail when all values are correct and no lived in UK but no university" in new Fixture {
      assertFieldRequired(NoUkLivedValidFormMap, "university", "university")
    }

    "fail when all values are correct and no civil servant but no school" in new Fixture {
      assertFieldRequired(NoCivilServantValidFormMap, "schoolName14to16", "schoolName14to16")
    }
    /*
    "fail when all values are correct but no haveDegree" in new Fixture {
      assertFieldRequired(FullValidFormMap, "haveDegree", "haveDegree")
    }
*/




    // TODO
/*
    "fail when liveInUKBetween14and18 is No and university field is missing" in new Fixture {
      assertFieldRequired(NoUkLivedAndNoCivilServantValidFormMap, "university", "university")
    }

    "fail when liveInUKBetween14and18 is No and universityDegreeCategory field is missing" in new Fixture {
      assertFieldRequired(NoUkLivedAndNoCivilServantValidFormMap, "universityDegreeCategory", "universityDegreeCategory")
    }
    */

    "transform form when is a civil servant with all valid fields to a question list" in new Fixture {
      val questionList = FullValidForm.exchange("Yes").questions
      questionList.size must be(8)
      questionList(0).answer.answer must be(Some("Yes"))
      questionList(0).answer.unknown must be(None)
      questionList(1).answer.answer must be(Some("AAA 111"))
      questionList(1).answer.unknown must be(None)
      questionList(2).answer.answer must be(Some("my school at 15"))
      questionList(2).answer.unknown must be(None)
      questionList(3).answer.answer must be(Some("my school at 17"))
      questionList(3).answer.unknown must be(None)
      questionList(4).answer.answer must be(Some("No"))
      questionList(4).answer.unknown must be(None)
      questionList(5).answer.answer must be(Some("Yes"))
      questionList(5).answer.unknown must be(None)
      questionList(6).answer.answer must be(Some("1"))
      questionList(6).answer.unknown must be(None)
      questionList(7).answer.answer must be(Some("(3)"))
      questionList(7).answer.unknown must be(None)
    }

    "transform form when is a civil servant with all Possible fields with prefer not to say" in new Fixture {
      val questionList = AllPreferNotToSayValidForm.exchange("Yes").questions
      questionList.size must be(8)
      questionList(0).answer.answer must be(Some("Yes"))
      questionList(0).answer.unknown must be(None)
      questionList(1).answer.answer must be(None)
      questionList(1).answer.unknown must be(Some(true))
      questionList(2).answer.answer must be(None)
      questionList(2).answer.unknown must be(Some(true))
      questionList(3).answer.answer must be(None)
      questionList(3).answer.unknown must be(Some(true))
      questionList(4).answer.answer must be(None)
      questionList(4).answer.unknown must be(Some(true))
      questionList(5).answer.answer must be(Some("Yes"))
      questionList(5).answer.unknown must be(None)
      questionList(6).answer.answer must be(None)
      questionList(6).answer.unknown must be(Some(true))
      questionList(7).answer.answer must be(None)
      questionList(7).answer.unknown must be(Some(true))
    }

    "transform form with no uk and no civil servant valid fields to a question list" in new Fixture {
      val questionList = NoUkLivedAndNoCivilServantValidForm.exchange("No").questions
      questionList.size must be(1)
      questionList(0).answer.answer must be(Some("No"))
      questionList(0).answer.unknown must be(None)
    }

    "transform form when is a civil servant and no uk lived with all valid fields to a question list" in new Fixture {
      val questionList = NoUkValidForm.exchange("Yes").questions
      questionList.size must be(4)
      questionList(0).answer.answer must be(Some("No"))
      questionList(0).answer.unknown must be(None)
      questionList(1).answer.answer must be(Some("Yes"))
      questionList(1).answer.unknown must be(None)
      questionList(2).answer.answer must be(Some("1"))
      questionList(2).answer.unknown must be(None)
      questionList(3).answer.answer must be(Some("(3)"))
      questionList(3).answer.unknown must be(None)
    }

    "transform form when is not a civil servant with all valid fields to a question list" in new Fixture {
      val questionList = NoCivilServantValidForm.exchange("No").questions
      questionList.size must be(5)
      questionList(0).answer.answer must be(Some("Yes"))
      questionList(0).answer.unknown must be(None)
      questionList(1).answer.answer must be(Some("AAA 111"))
      questionList(1).answer.unknown must be(None)
      questionList(2).answer.answer must be(Some("my school at 15"))
      questionList(2).answer.unknown must be(None)
      questionList(3).answer.answer must be(Some("my school at 17"))
      questionList(3).answer.unknown must be(None)
      questionList(4).answer.answer must be(Some("No"))
      questionList(4).answer.unknown must be(None)
    }

    "sanitize data should respect values when liveInUKBetween14and18 is Yes and haveDegree is Yes" in new Fixture {
      EducationQuestionnaireFormExamples.FullValidForm.sanitizeData must be (EducationQuestionnaireFormExamples.FullValidForm)
    }

    "sanitize data should sanitize values when liveInUKBetween14and18 is No and haveDegree is No" in new Fixture {
      EducationQuestionnaireFormExamples.NoUkAndNoHaveDegreeFullInvalidForm.sanitizeData must be
      (EducationQuestionnaireFormExamples.NoUkLivedAndNoCivilServantValidForm)
    }
  }

  trait Fixture {

    val FullValid = (EducationQuestionnaireFormExamples.FullValidForm, EducationQuestionnaireForm.form.fill(
      EducationQuestionnaireFormExamples.FullValidForm))

    val AllPreferNotToSayValid = (EducationQuestionnaireFormExamples.AllPreferNotToSayValidForm, EducationQuestionnaireForm.form.fill(
      EducationQuestionnaireFormExamples.AllPreferNotToSayValidForm))

    val NoUkValid = (EducationQuestionnaireFormExamples.NoUkValidForm, EducationQuestionnaireForm.form.fill(
      EducationQuestionnaireFormExamples.NoUkValidForm))

    val NoCivilServantValid = (EducationQuestionnaireFormExamples.NoCivilServantValidForm, EducationQuestionnaireForm.form.fill(
      EducationQuestionnaireFormExamples.NoCivilServantValidForm))

    val NoUkLivedAndNoCivilServantValid = (EducationQuestionnaireFormExamples.NoUkLivedAndNoCivilServantValidForm,
      EducationQuestionnaireForm.form.fill(EducationQuestionnaireFormExamples.NoUkLivedAndNoCivilServantValidForm))

    def assertFieldRequired(formMap: Map[String, String], expectedError: String, fieldKey: String*) =
      assertFormError(expectedError, formMap ++ fieldKey.map(k => k -> ""))

    def assertFormError(expectedKey: String, invalidFormValues: Map[String, String]) = {
      val invalidForm = EducationQuestionnaireForm.form.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.key) mustBe Seq(expectedKey)
    }
  }

}
