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

package model.exchange

import model.LocationId
import play.api.libs.json.{Json, OFormat}

case class SdipLocation(id: LocationId, name: String)

object SdipLocation {

  implicit val sdipLocationFormatter: OFormat[SdipLocation] = Json.format[SdipLocation]

  val Locations = List(
    SdipLocation(LocationId("location1"), "Location 1"),
    SdipLocation(LocationId("location2"), "Location 2"),
    SdipLocation(LocationId("location3"), "Location 3"),
    SdipLocation(LocationId("location4"), "Location 4"),
    SdipLocation(LocationId("location5"), "Location 5"),
    SdipLocation(LocationId("location6"), "Location 6"),
    SdipLocation(LocationId("location7"), "Location 7"),
    SdipLocation(LocationId("location8"), "Location 8"),
  )
}
