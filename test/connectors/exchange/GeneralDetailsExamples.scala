/*
 * Copyright 2019 HM Revenue & Customs
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
  val FullDetails = GeneralDetails("firstName", "lastName", "preferredName", "email", LocalDate.now(), outsideUk = false,
    FullAddress, Some("postCode"), None, None, Some("1234567"), Some(CivilServiceExperienceDetails(applicable = false)), None, None)
  val SdipFullDetailsWithEdipCompleted = GeneralDetails("firstName", "lastName", "preferredName", "email", LocalDate.now(), outsideUk = false,
    FullAddress, Some("postCode"), None, None, Some("1234567"), Some(CivilServiceExperienceDetails(applicable = false)), Some(true), None)
}
