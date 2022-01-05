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

package connectors.exchange

import mappings.AddressExamples._
import org.joda.time.LocalDate

object GeneralDetailsExamples {
  val FullDetails = GeneralDetails("firstName", "lastName", "preferredName", "email", dateOfBirth = LocalDate.now(), outsideUk = false,
    FullAddress, Some("postCode"), fsacIndicator = None, country = None, phone = Some("1234567"),
    civilServiceExperienceDetails = Some(CivilServiceExperienceDetails(applicable = false)),
    edipCompleted = None, edipYear = None, otherInternshipCompleted = None, otherInternshipName = None, otherInternshipYear = None,
    updateApplicationStatus = None
  )
  val SdipFullDetailsWithEdipCompleted = GeneralDetails("firstName", "lastName", "preferredName", "email", LocalDate.now(), outsideUk = false,
    FullAddress, Some("postCode"), fsacIndicator = None, country = None, phone = Some("1234567"),
    civilServiceExperienceDetails = Some(CivilServiceExperienceDetails(applicable = false)), edipCompleted = Some(true), edipYear = Some("2020"),
    otherInternshipCompleted = None, otherInternshipName = None, otherInternshipYear = None, updateApplicationStatus = None
  )
}
