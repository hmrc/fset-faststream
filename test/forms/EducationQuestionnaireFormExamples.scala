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

object EducationQuestionnaireFormExamples {

  val FullValidForm = EducationQuestionnaireForm.Data(
    liveInUKBetween14and18 = "Yes",
    postcode = Some("AAA 111"),
    preferNotSayPostcode = None,
    schoolName14to16 = Some("my school at 15"),
    schoolId14to16 = None,
    preferNotSaySchoolName14to16 = None,
    schoolType14to16 = Some("state funded"),
    schoolName16to18 = Some("my school at 17"),
    schoolId16to18 = None,
    preferNotSaySchoolName16to18 = None,
    freeSchoolMeals = Some("No"),
    isCandidateCivilServant = "Yes",
    haveDegree = Some("Yes"),
    university = Some("1"),
    preferNotSayUniversity = None,
    universityDegreeCategory = Some("(3)"),
    preferNotSayUniversityDegreeCategory = None)

  val AllPreferNotToSayValidForm = EducationQuestionnaireForm.Data(
    liveInUKBetween14and18 = "Yes",
    postcode = None,
    preferNotSayPostcode = Some(true),
    schoolName14to16 = None,
    schoolId14to16 = None,
    preferNotSaySchoolName14to16 = Some(true),
    schoolType14to16 = Some("I don't know/prefer not to say"),
    schoolName16to18 = None,
    schoolId16to18 = None,
    preferNotSaySchoolName16to18 = Some(true),
    freeSchoolMeals = Some("I don't know/prefer not to say"),
    isCandidateCivilServant = "Yes",
    haveDegree = Some("Yes"),
    university = None,
    preferNotSayUniversity = Some(true),
    universityDegreeCategory = None,
    preferNotSayUniversityDegreeCategory = Some(true))

  val NotUkLivedAndNoDegreeValidForm = EducationQuestionnaireForm.Data(
    liveInUKBetween14and18 = "No",
    postcode = None,
    preferNotSayPostcode = None,
    schoolName14to16 = None,
    schoolId14to16 = None,
    preferNotSaySchoolName14to16 = None,
    schoolType14to16 = None,
    schoolName16to18 = None,
    schoolId16to18 = None,
    preferNotSaySchoolName16to18 = None,
    freeSchoolMeals = None,
    isCandidateCivilServant = "No",
    haveDegree = Some("No"),
    university = None,
    preferNotSayUniversity = None,
    universityDegreeCategory = None,
    preferNotSayUniversityDegreeCategory = None)

  val NotUkLivedAndHaveDegreeValidForm = EducationQuestionnaireForm.Data(
    liveInUKBetween14and18 = "No",
    postcode = None,
    preferNotSayPostcode = None,
    schoolName14to16 = None,
    schoolId14to16 = None,
    preferNotSaySchoolName14to16 = None,
    schoolType14to16 = None,
    schoolName16to18 = None,
    schoolId16to18 = None,
    preferNotSaySchoolName16to18 = None,
    freeSchoolMeals = None,
    isCandidateCivilServant = "Yes",
    haveDegree = Some("Yes"),
    university = Some("1"),
    preferNotSayUniversity = None,
    universityDegreeCategory = Some("(3)"),
    preferNotSayUniversityDegreeCategory = None)

  val LivedInUKAndNoDegreeValidForm = EducationQuestionnaireForm.Data(
    liveInUKBetween14and18 = "Yes",
    postcode = Some("AAA 111"),
    preferNotSayPostcode = None,
    schoolName14to16 = Some("my school at 15"),
    schoolId14to16 = None,
    preferNotSaySchoolName14to16 = None,
    schoolType14to16 = Some("state funded"),
    schoolName16to18 = Some("my school at 17"),
    schoolId16to18 = None,
    preferNotSaySchoolName16to18 = None,
    freeSchoolMeals = Some("No"),
    isCandidateCivilServant = "No",
    haveDegree = Some("No"),
    university = None,
    preferNotSayUniversity = None,
    universityDegreeCategory = None,
    preferNotSayUniversityDegreeCategory = None)

  val NotUkLivedAndNoHaveDegreeFullInvalidForm = EducationQuestionnaireForm.Data(
    liveInUKBetween14and18 = "No",
    postcode = Some("AAA 111"),
    preferNotSayPostcode = None,
    schoolName14to16 = Some("my school at 15"),
    schoolId14to16 = None,
    preferNotSaySchoolName14to16 = None,
    schoolType14to16 = Some("state-funded"),
    schoolName16to18 = Some("my school at 17"),
    schoolId16to18 = None,
    preferNotSaySchoolName16to18 = None,
    freeSchoolMeals = Some("No"),
    isCandidateCivilServant = "No",
    haveDegree = Some("No"),
    university = Some("1"),
    preferNotSayUniversity = None,
    universityDegreeCategory = Some("(3)"),
    preferNotSayUniversityDegreeCategory = None)

  val FullValidFormMap = Map(
    "liveInUKBetween14and18" -> "Yes",
    "postcodeQ" -> "SL1 3GQ",
    "schoolName14to16" -> "my school at 15",
    "schoolType14to16" -> "state funded",
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
    "schoolType14to16" -> "I don't know/prefer not to say",
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
    "schoolType14to16" -> "state funded",
    "schoolName16to18" -> "school2",
    "freeSchoolMeals" -> "Yes",
    "isCandidateCivilServant" -> "No",
    "haveDegree" -> "No"
  )
}
