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

package model.persisted

import org.joda.time.LocalDate
import org.scalatest.FunSuite

object PersonalDetailsExamples extends FunSuite {
  val JohnDoe = PersonalDetails("John", "Doe", "johnd", dateOfBirth = LocalDate.now().minusYears(20), edipCompleted = Some(false),
    edipYear = None, otherInternshipCompleted = Some(false), otherInternshipName = None, otherInternshipYear = None)
  val SdipJohnDoe = PersonalDetails("John", "Doe", "johnd", dateOfBirth = LocalDate.now().minusYears(20), edipCompleted = Some(true),
    edipYear = Some("2020"), otherInternshipCompleted = Some(false), otherInternshipName = None, otherInternshipYear = None)
//  val JohnDoe2 = PersonalDetails2("John", "Doe", "johnd", dateOfBirth = LocalDate.now().minusYears(20), edipCompleted = Some(false),
//    edipYear = None, otherInternshipCompleted = Some(false), otherInternshipName = None, otherInternshipYear = None)
//  val SdipJohnDoe2 = PersonalDetails2("John", "Doe", "johnd", dateOfBirth = LocalDate.now().minusYears(20), edipCompleted = Some(true),
//    edipYear = Some("2020"), otherInternshipCompleted = Some(false), otherInternshipName = None, otherInternshipYear = None)
}
