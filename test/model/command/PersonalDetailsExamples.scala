/*
 * Copyright 2020 HM Revenue & Customs
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

package model.command

import model.Address
import model.persisted.PersonalDetails
import org.joda.time.LocalDate

object PersonalDetailsExamples {

  val completeGeneralDetails = GeneralDetails("TestName", "TestSurname", "Jo Test", "jo@go.sx", dateOfBirth = LocalDate.parse("1972-11-23"),
    outsideUk = false, Address("Test street"), postCode = None, fsacIndicator = None, country = None, phone = "098762532",
    civilServiceExperienceDetails = None, edipCompleted = None, edipYear = None, otherInternshipCompleted = None, otherInternshipName = None,
    otherInternshipYear = None, updateApplicationStatus = None)

  val completed = PersonalDetails("firstname", "lastname", "preferedname", dateOfBirth = LocalDate.now(),
    edipCompleted = Some(false), edipYear = None, otherInternshipCompleted = Some(false), otherInternshipName = None,
    otherInternshipYear = None)

  val personalDetails = PersonalDetails("TestName", "TestSurname", "Jo Test", dateOfBirth = LocalDate.parse("1972-11-23"),
    edipCompleted = Some(false), edipYear = None, otherInternshipCompleted = Some(false), otherInternshipName = None,
    otherInternshipYear = None)
}
