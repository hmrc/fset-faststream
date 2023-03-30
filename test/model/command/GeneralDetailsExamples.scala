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

package model.command

import java.time.LocalDate
import model.AddressExamples._
import model.{ CivilServiceExperienceDetails, FSACIndicator }

object GeneralDetailsExamples {
  val CandidateContactDetailsUK = GeneralDetails("John", "Doe", "johnd", "johndoe@test.com", LocalDate.now().minusYears(20),
    outsideUk = false, FullAddress, Some("A1 B23"), Some(FSACIndicator("London", "London")), country = None, phone = "1234567890",
    Some(CivilServiceExperienceDetails(applicable = false)), edipCompleted = Some(false), edipYear = None,
    otherInternshipCompleted = Some(false), otherInternshipName = None, otherInternshipYear = None, updateApplicationStatus = Some(true))
  val CandidateContactDetailsUKSdip = GeneralDetails("John", "Doe", "johnd", "johndoe@test.com", LocalDate.now().minusYears(20),
    outsideUk = false, FullAddress, Some("A1 B23"), Some(FSACIndicator("London", "London")), country = None, phone = "1234567890",
    Some(CivilServiceExperienceDetails(applicable = false)), edipCompleted = Some(true), edipYear = Some("2020"),
    otherInternshipCompleted = Some(false), otherInternshipName = None, otherInternshipYear = None, updateApplicationStatus = Some(true))
}
