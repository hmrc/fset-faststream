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

import model.Address
import model.Commands.{PhoneNumber, PostCode}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, OFormat, __}

case class ContactDetailsUserId(userId: String)

object ContactDetailsUserId {
  implicit val mongoFormat: OFormat[ContactDetailsUserId] = Json.format[ContactDetailsUserId]
}

case class ContactDetailsUserIdPostcode(userId: String, postcode: PostCode)

object ContactDetailsUserIdPostcode {

  val root = "contact-details"
  val mongoFormat: Format[ContactDetailsUserIdPostcode] = (
    (__ \ "userId").format[String] and
      (__ \ root \ "postCode").format[PostCode]
    )(ContactDetailsUserIdPostcode.apply, unlift(o => Some(Tuple.fromProductTyped(o))))
}

case class ContactDetails(outsideUk: Boolean,
                          address: Address,
                          postCode: Option[PostCode],
                          country: Option[String],
                          email: String,
                          phone: PhoneNumber)

object ContactDetails {
  implicit val contactDetailsFormat: OFormat[ContactDetails] = Json.format[ContactDetails]

  // Provide an explicit mongo format here to deal with the sub-document root
  val root = "contact-details"
  val mongoFormat: Format[ContactDetails] = (
    (__ \ root \ "outsideUk").format[Boolean] and
      (__ \ root \ "address").format[Address] and
      (__ \ root \ "postCode").formatNullable[PostCode] and
      (__ \ root \ "country").formatNullable[String] and
      (__ \ root \ "email").format[String] and
      (__ \ root \ "phone").format[PhoneNumber]
    )(ContactDetails.apply, unlift(o => Some(Tuple.fromProductTyped(o))))
}
