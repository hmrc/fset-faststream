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

package mappings

import mappings.Mappings._
import play.api.data.Forms._
import play.api.libs.json.{Format, Json}

case class Address(line1: String, line2: Option[String], line3: Option[String], line4: Option[String])

object Address {
  val MaxLineLength = 100

  implicit val addressFormat: Format[Address] = Json.format[Address]

  val EmptyAddress: Address = Address("", None, None, None)
}

object AddressMapping {
  def address = mapping(
    "line1" -> nonEmptyTrimmedText("error.address.required", Address.MaxLineLength),
    "line2" -> optional(nonEmptyTrimmedText("error.address.required", Address.MaxLineLength)),
    "line3" -> optional(nonEmptyTrimmedText("error.address.required", Address.MaxLineLength)),
    "line4" -> optional(nonEmptyTrimmedText("error.address.required", Address.MaxLineLength))
  )(Address.apply)(Address.unapply)
}
