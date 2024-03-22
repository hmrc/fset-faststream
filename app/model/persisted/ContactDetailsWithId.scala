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


case class ContactDetailsWithId(userId: String,
                                address: Address,
                                postCode: Option[PostCode],
                                outsideUk: Boolean,
                                email: String,
                                phone: Option[PhoneNumber])

object ContactDetailsWithId {
  implicit val format: OFormat[ContactDetailsWithId] = Json.format[ContactDetailsWithId]

  // Provide an explicit mongo format here to deal with the sub-document root
  val subRoot = "contact-details"
  val mongoFormat: Format[ContactDetailsWithId] = (
    (__ \ "userId").format[String] and
      (__ \ subRoot \ "address").format[Address] and
      (__ \ subRoot \ "postCode").formatNullable[PostCode] and
      (__ \ subRoot \ "outsideUk").format[Boolean] and
      (__ \ subRoot \ "email").format[String] and
      (__ \ subRoot \ "phone").formatNullable[PhoneNumber]
    )(ContactDetailsWithId.apply, unlift(ContactDetailsWithId.unapply))
}
