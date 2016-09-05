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

import forms.QuestionnaireEducationInfoForm

object QuestionnaireEducationInfoFormExamples {

  val FullValidForm = QuestionnaireEducationInfoForm.Data(
    "Yes",
    Some("AAA 111"),
    None,
    Some("my school at 15"),
    None,
    Some("my school at 17"),
    None,
    Some("No"),
    Some("University"),
    None,
    Some("(3)"),
    None)

  val AllPreferNotToSayValidForm = QuestionnaireEducationInfoForm.Data(
    "Yes",
    None,
    Some(true),
    None,
    Some(true),
    None,
    Some(true),
    Some("I don't know/prefer not to say"),
    None,
    Some(true),
    None,
    Some(true))

  val NoUkLivedValidForm = QuestionnaireEducationInfoForm.Data(
    "No",
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Some("University"),
    None,
    Some("(3)"),
    None)


  val NoUkFullInvalidForm = QuestionnaireEducationInfoForm.Data(
    "No",
    Some("AAA 111"),
    None,
    Some("my school at 15"),
    None,
    Some("my school at 17"),
    None,
    Some("No"),
    Some("University"),
    None,
    Some("(3)"),
    None)

  val FullValidFormMap = Map(
    "liveInUKBetween14and18" -> "Yes",
    "postcodeQ" -> "AAA 111",
    "preferNotSayPostcode" -> "",
    "schoolName14to16" -> "my school at 15",
    "preferNotSaySchoolName14to16" -> "",
    "schoolName16to18" -> "my school at 17",
    "preferNotSaySchoolName16to18" -> "",
    "freeSchoolMeals" -> "No",
    "university" -> "University",
    "preferNotSay_university" -> "",
    "universityDegreeCategory" -> "(3)",
    "preferNotSayUniversityDegreeCategory" -> ""
  )

  val AllPreferNotToSayFormMap = Map(
    "liveInUKBetween14and18" -> "Yes",
    "postcodeQ" -> "",
    "preferNotSayPostcode" -> "Yes",
    "schoolName14to16" -> "",
    "preferNotSaySchoolName14to16" -> "Yes",
    "schoolName16to18" -> "",
    "preferNotSaySchoolName16to18" -> "Yes",
    "freeSchoolMeals" -> "I don't know/prefer not to say",
    "university" -> "",
    "preferNotSay_university" -> "Yes",
    "universityDegreeCategory" -> "",
    "preferNotSayUniversityDegreeCategory" -> "Yes"
  )

  val NoUkLivedValidFormMap = Map(
    "liveInUKBetween14and18" -> "No",
    "postcodeQ" -> "",
    "preferNotSayPostcode" -> "",
    "schoolName14to16" -> "",
    "preferNotSaySchoolName14to16" -> "",
    "schoolName16to18" -> "",
    "preferNotSaySchoolName16to18" -> "",
    "freeSchoolMeals" -> "No",
    "university" -> "University",
    "preferNotSay_university" -> "",
    "universityDegreeCategory" -> "(3)",
    "preferNotSayUniversityDegreeCategory" -> ""
  )
}
