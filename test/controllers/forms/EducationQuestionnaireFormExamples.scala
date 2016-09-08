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

import forms.EducationQuestionnaireForm

object EducationQuestionnaireFormExamples {

  val FullValidForm = EducationQuestionnaireForm.Data(
    "Yes",
    Some("AAA 111"),
    None,
    Some("my school at 15"),
    None,
    Some("my school at 17"),
    None,
    Some("No"),
    Some("Yes"),
    Some("1"),
    None,
    Some("(3)"),
    None)

  val AllPreferNotToSayValidForm = EducationQuestionnaireForm.Data(
    "Yes",
    None,
    Some(true),
    None,
    Some(true),
    None,
    Some(true),
    Some("I don't know/prefer not to say"),
    Some("Yes"),
    None,
    Some(true),
    None,
    Some(true))

  val NoUkLivedAndNoCivilServantValidForm = EducationQuestionnaireForm.Data(
    "No",
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None)

  val NoUkValidForm = EducationQuestionnaireForm.Data(
    "No",
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Some("Yes"),
    Some("1"),
    None,
    Some("(3)"),
    None)

  val NoCivilServantValidForm = EducationQuestionnaireForm.Data(
    "Yes",
    Some("AAA 111"),
    None,
    Some("my school at 15"),
    None,
    Some("my school at 17"),
    None,
    Some("No"),
    None,
    None,
    None,
    None,
    None)

  val NoUkAndNoHaveDegreeFullInvalidForm = EducationQuestionnaireForm.Data(
    "No",
    Some("AAA 111"),
    None,
    Some("my school at 15"),
    None,
    Some("my school at 17"),
    None,
    Some("No"),
    Some("No"),
    Some("1"),
    None,
    Some("(3)"),
    None)

  val FullValidFormMap = Map(
    "liveInUKBetween14and18" -> "Yes",
    "postcodeQ" -> "AAA 111",
    "schoolName14to16" -> "my school at 15",
    "schoolName16to18" -> "my school at 17",
    "freeSchoolMeals" -> "No",
    "haveDegree" -> "Yes",
    "university" -> "1",
    "universityDegreeCategory" -> "(3)"//,
  )

  val AllPreferNotToSayFormMap = Map(
    "liveInUKBetween14and18" -> "Yes",
    "preferNotSayPostcode" -> "Yes",
    "preferNotSaySchoolName14to16" -> "true",
    "preferNotSaySchoolName16to18" -> "true",
    "freeSchoolMeals" -> "I don't know/prefer not to say",
    "haveDegree" -> "Yes",
    "preferNotSay_university" -> "true",
    "preferNotSayUniversityDegreeCategory" -> "true"
  )

  val NoUkLivedAndNoCivilServantValidFormMap = Map(
    "liveInUKBetween14and18" -> "No"
  )

  val NoUkLivedValidFormMap = Map(
    "liveInUKBetween14and18" -> "No",
    "haveDegree" -> "Yes",
    "university" -> "1",
    "universityDegreeCategory" -> "(E)"
  )

  val NoCivilServantValidFormMap = Map(
    "liveInUKBetween14and18" -> "Yes",
    "postcodeQ" -> "AAA 111",
    "schoolName14to16" -> "school1",
    "schoolName16to18" -> "school2",
    "freeSchoolMeals" -> "Yes"
  )
}
