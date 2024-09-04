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
    SdipLocation(LocationId("Belfast"), "Belfast"),
    SdipLocation(LocationId("Birmingham"), "Birmingham"),
    SdipLocation(LocationId("Blackpool"), "Blackpool"),
    SdipLocation(LocationId("Bristol"), "Bristol"),
    SdipLocation(LocationId("Cardiff"), "Cardiff"),
    SdipLocation(LocationId("Coventry"), "Coventry"),
    SdipLocation(LocationId("Darlington"), "Darlington"),
    SdipLocation(LocationId("Edinburgh"), "Edinburgh"),
    SdipLocation(LocationId("Exeter"), "Exeter"),
    SdipLocation(LocationId("Glasgow"), "Glasgow"),
    SdipLocation(LocationId("Leeds"), "Leeds"),
    SdipLocation(LocationId("Liverpool"), "Liverpool"),
    SdipLocation(LocationId("London"), "London"),
    SdipLocation(LocationId("Manchester"), "Manchester"),
    SdipLocation(LocationId("Newcastle"), "Newcastle"),
    SdipLocation(LocationId("Newport"), "Newport"),
    SdipLocation(LocationId("Nottingham"), "Nottingham"),
    SdipLocation(LocationId("Reading"), "Reading"),
    SdipLocation(LocationId("Sheffield"), "Sheffield"),
    SdipLocation(LocationId("Titchfield"), "Titchfield"),
    SdipLocation(LocationId("Wolverhampton"), "Wolverhampton"),
    SdipLocation(LocationId("York"), "York")
  )
}
