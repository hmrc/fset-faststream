/*
 * Copyright 2023 HM Revenue & Customs
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

package repositories.application

trait DiversityQuestionsText {
  val genderIdentity = "What is your gender identity?"
  val sexualOrientation = "What is your sexual orientation?"
  val ethnicGroup = "What is your ethnic group?"
  val englishLanguage = "Is English your first language?"
  val liveInUkAged14to18 = "Did you live in the UK between the ages of 14 and 18?"
  val postcodeAtAge14 = "What was your home postcode when you were 14?"
  val schoolNameAged14to16 = "Aged 14 to 16 what was the name of your school?"
  val schoolTypeAged14to16 = "Which type of school was this?"
  val schoolNameAged16to18 = "Aged 16 to 18 what was the name of your school or college? (if applicable)"
  val eligibleForFreeSchoolMeals = "Were you at any time eligible for free school meals?"
  val doYouHaveADegree = "Do you have a degree?"
  val universityName = "What is the name of the university you received your degree from?"
  val categoryOfDegree = "Which category best describes your degree?"
  val lowerSocioEconomicBackground = "Do you consider yourself to come from a lower socio-economic background?"
  val parentOrGuardianQualificationsAtAge18 = "Do you have a parent or guardian that completed a university degree course, " +
    "or qualifications below degree level, by the time you were 18?"
  val highestEarningParentOrGuardianTypeOfWorkAtAge14 = "When you were 14, what kind of work did your highest-earning parent or guardian do?"
  val employeeOrSelfEmployed = "Did they work as an employee or were they self-employed?"
  val sizeOfPlaceOfWork = "Which size would best describe their place of work?"
  val superviseEmployees = "Did they supervise employees?"
}
