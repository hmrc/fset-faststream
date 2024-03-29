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

package model.persisted

import model.AddressExamples._

object ContactDetailsExamples {
  val ContactDetailsUK = ContactDetails(
    outsideUk = false, FullAddress, postCode = Some("A1 B23"), country = None, email = "johndoe@test.com", phone = "1234567890"
  )
  val ContactDetailsOutsideUK = ContactDetails(
    outsideUk = true, FullAddress, postCode = None, country = Some("Mongolia"), email = "wilfredo.gomez@test.com", phone = "0987456123"
  )
}
