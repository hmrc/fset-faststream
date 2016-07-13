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

package models.frameworks

import play.api.libs.json.Json

case class Preference(
  region: String,
  location: String,
  firstFramework: String,
  secondFramework: Option[String]
)

case class Alternatives(
  location: Boolean,
  framework: Boolean
)

//type NoSecondPreference = Boolean
case class LocationAndSchemeSelection(
  firstLocation: Preference,
  secondLocationIntended: Option[Boolean],
  secondLocation: Option[Preference],
  alternatives: Option[Alternatives]
)

object Preference {
  implicit val jsonFormatPref = Json.format[Preference]
}

object Alternatives {
  implicit val jsonFormatAlt = Json.format[Alternatives]
}

object LocationAndSchemeSelection {
  implicit val jsonFormat = Json.format[LocationAndSchemeSelection]

  val empty = LocationAndSchemeSelection(firstLocation = Preference("", "", "", None), None, None, None)
}
