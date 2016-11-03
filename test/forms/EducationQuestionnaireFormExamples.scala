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

package forms

object EducationQuestionnaireFormExamples {

  val FullValidForm = EducationQuestionnaireForm.Data(
    "Yes",
    Some("AAA 111"),
    None,
    Some("my school at 15"),
    None,
    None,
    Some("my school at 17"),
    None,
    None,
    Some("No"),
    "Yes",
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
    None,
    Some(true),
    None,
    None,
    Some(true),
    Some("I don't know/prefer not to say"),
    "Yes",
    Some("Yes"),
    None,
    Some(true),
    None,
    Some(true))

  val NotUkLivedAndNoDegreeValidForm = EducationQuestionnaireForm.Data(
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
    "No",
    Some("No"),
    None,
    None,
    None,
    None)

  val NotUkLivedAndHaveDegreeValidForm = EducationQuestionnaireForm.Data(
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
    "Yes",
    Some("Yes"),
    Some("1"),
    None,
    Some("(3)"),
    None)

  val LivedInUKAndNoDegreeValidForm = EducationQuestionnaireForm.Data(
    "Yes",
    Some("AAA 111"),
    None,
    Some("my school at 15"),
    None,
    None,
    Some("my school at 17"),
    None,
    None,
    Some("No"),
    "No",
    Some("No"),
    None,
    None,
    None,
    None)

  val NotUkLivedAndNoHaveDegreeFullInvalidForm = EducationQuestionnaireForm.Data(
    "No",
    Some("AAA 111"),
    None,
    Some("my school at 15"),
    None,
    None,
    Some("my school at 17"),
    None,
    None,
    Some("No"),
    "No",
    Some("No"),
    Some("1"),
    None,
    Some("(3)"),
    None)

  val FullValidFormMap = Map(
    "liveInUKBetween14and18" -> "Yes",
    "postcodeQ" -> "SL1 3GQ",
    "schoolName14to16" -> "my school at 15",
    "schoolName16to18" -> "my school at 17",
    "freeSchoolMeals" -> "No",
    "isCandidateCivilServant" -> "Yes",
    "haveDegree" -> "Yes",
    "university" -> "1",
    "universityDegreeCategory" -> "(3)"
  )

  val AllPreferNotToSayFormMap = Map(
    "liveInUKBetween14and18" -> "Yes",
    "preferNotSayPostcode" -> "Yes",
    "preferNotSaySchoolName14to16" -> "true",
    "preferNotSaySchoolName16to18" -> "true",
    "freeSchoolMeals" -> "I don't know/prefer not to say",
    "isCandidateCivilServant" -> "Yes",
    "haveDegree" -> "Yes",
    "preferNotSay_university" -> "true",
    "preferNotSayUniversityDegreeCategory" -> "true"
  )

  val NotUkLivedAndNoDegreeValidFormMap = Map(
    "liveInUKBetween14and18" -> "No",
    "isCandidateCivilServant" -> "No",
    "haveDegree" -> "No"
  )

  val NotUkLivedAndHaveDegreeValidFormMap = Map(
    "liveInUKBetween14and18" -> "No",
    "isCandidateCivilServant" -> "Yes",
    "haveDegree" -> "Yes",
    "university" -> "1",
    "universityDegreeCategory" -> "(E)"
  )

  val LivedInUKAndNoDegreeValidFormMap = Map(
    "liveInUKBetween14and18" -> "Yes",
    "postcodeQ" -> "SL1 3GQ",
    "schoolName14to16" -> "school1",
    "schoolName16to18" -> "school2",
    "freeSchoolMeals" -> "Yes",
    "isCandidateCivilServant" -> "No",
    "haveDegree" -> "No"
  )
}
