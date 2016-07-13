/*
 * Copyright 2016 HM Revenue & Customs
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

import forms.Mappings.nonEmptyTrimmedText
import play.api.data.Forms._

case class Address(line1: String, line2: Option[String], line3: Option[String], line4: Option[String])

object Address {

  def address = mapping(
    "line1" -> nonEmptyTrimmedText("error.address.required", 1024),
    "line2" -> optional(nonEmptyTrimmedText("error.address.required", 1024)),
    "line3" -> optional(nonEmptyTrimmedText("error.address.required", 1024)),
    "line4" -> optional(nonEmptyTrimmedText("error.address.required", 1024))
  )(Address.apply)(Address.unapply)

}
