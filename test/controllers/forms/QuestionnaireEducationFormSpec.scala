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
import controllers.forms.QuestionnaireEducationInfoFormExamples._
import forms.QuestionnaireEducationInfoForm

class QuestionnaireEducationFormSpec extends BaseSpec {

  "the education form" should {
    "be valid when all values are correct" in new Fixture {
      val (data, form) = FullValid
      form.get must be(data)
    }

    "be valid when all possible values are <<I don't know/ prefer not to say>>" in new Fixture {
      val (data, form) = AllPreferNotToSayValid
      form.get must be(data)
    }

    "fail when all values are correct but no liveInUKBetween14and18" in new Fixture {
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

    "fail when no freeSchoolMeals" in new Fixture {
      assertFieldRequired(FullValidFormMap, "freeSchoolMeals", "freeSchoolMeals")
    }

    "fail when all values are correct but no university" in new Fixture {
      assertFieldRequired(FullValidFormMap, "university", "university")
    }

    "fail when all values are correct but no universityDegreeCategory" in new Fixture {
      assertFieldRequired(FullValidFormMap, "universityDegreeCategory", "universityDegreeCategory")
    }

    "be valid when liveInUKBetween14and18 is No and the postcodeQ, schoolName14to16, schoolName16to18 and " +
      "freeSchoolMeals are not populated" in new Fixture {
      val (data, form) = NoUkLivedValid
      form.get must be(data)
    }

    "fail when liveInUKBetween14and18 is No and university field is missing" in new Fixture {
      assertFieldRequired(NoUkLivedValidFormMap, "university", "university")
    }

    "fail when liveInUKBetween14and18 is No and universityDegreeCategory field is missing" in new Fixture {
      assertFieldRequired(NoUkLivedValidFormMap, "universityDegreeCategory", "universityDegreeCategory")
    }

    "transform form with all valid fields to a question list" in new Fixture {
      val questionList = FullValidForm.exchange.questions
      questionList.size must be(7)
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
      questionList(5).answer.answer must be(Some("University"))
      questionList(5).answer.unknown must be(None)
      questionList(6).answer.answer must be(Some("(3)"))
      questionList(6).answer.unknown must be(None)
    }

    "transform form with all Possible fields with prefer not to say" in new Fixture {
      val questionList = AllPreferNotToSayValidForm.exchange.questions
      questionList.size must be(7)
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
      questionList(5).answer.answer must be(None)
      questionList(5).answer.unknown must be(Some(true))
      questionList(6).answer.answer must be(None)
      questionList(6).answer.unknown must be(Some(true))
    }

    "transform form with no  uk valid valid fields to a question list" in new Fixture {
      val questionList = NoUkLivedValidForm.exchange.questions
      questionList.size must be(3)
      questionList(0).answer.answer must be(Some("No"))
      questionList(0).answer.unknown must be(None)
      questionList(1).answer.answer must be(Some("University"))
      questionList(1).answer.unknown must be(None)
      questionList(2).answer.answer must be(Some("(3)"))
      questionList(2).answer.unknown must be(None)
    }

    "sanitize data should respect values when liveInUKBetween14and18 is Yes" in new Fixture {
      QuestionnaireEducationInfoFormExamples.FullValidForm.sanitizeData must be (QuestionnaireEducationInfoFormExamples.FullValidForm)
    }

    "sanitize data should sanitize values when liveInUKBetween14and18 is No" in new Fixture {
      QuestionnaireEducationInfoFormExamples.NoUkFullInvalidForm.sanitizeData must be (QuestionnaireEducationInfoFormExamples.NoUkLivedValidForm)
    }
  }

  trait Fixture {

    val FullValid = (QuestionnaireEducationInfoFormExamples.FullValidForm, QuestionnaireEducationInfoForm.form.fill(
      QuestionnaireEducationInfoFormExamples.FullValidForm))

    val AllPreferNotToSayValid = (QuestionnaireEducationInfoFormExamples.AllPreferNotToSayValidForm, QuestionnaireEducationInfoForm.form.fill(
      QuestionnaireEducationInfoFormExamples.AllPreferNotToSayValidForm))

    val NoUkLivedValid = (QuestionnaireEducationInfoFormExamples.NoUkLivedValidForm, QuestionnaireEducationInfoForm.form.fill(
      QuestionnaireEducationInfoFormExamples.NoUkLivedValidForm))

    def assertFieldRequired(formMap: Map[String, String], expectedError: String, fieldKey: String*) =
      assertFormError(expectedError, formMap ++ fieldKey.map(k => k -> ""))

    def assertFormError(expectedKey: String, invalidFormValues: Map[String, String]) = {
      val invalidForm = QuestionnaireEducationInfoForm.form.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.key) mustBe Seq(expectedKey)
    }
  }

}
