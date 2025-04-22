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

package model

import org.mongodb.scala.bson.BsonValue
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsString, JsSuccess, Json, OFormat, Reads, Writes, __}
import uk.gov.hmrc.mongo.play.json.Codecs

case class LocationId(value: String) {
  implicit override def toString: String = value
}

object LocationId {
  // Custom json formatter to serialise to a string
  val locationIdWritesFormat: Writes[LocationId] = Writes[LocationId](location => JsString(location.value))
  val locationIdReadsFormat: Reads[LocationId] = Reads[LocationId](location => JsSuccess(LocationId(location.as[String])))

  implicit val locationIdFormat: Format[LocationId] = Format(locationIdReadsFormat, locationIdWritesFormat)

  implicit class BsonOps(val locationId: LocationId) extends AnyVal {
    def toBson: BsonValue = Codecs.toBson(locationId)
  }
}

case class SelectedLocations(locations: List[LocationId], interests: List[String])

object SelectedLocations {
  implicit val selectedLocationsFormat: OFormat[SelectedLocations] = Json.format[SelectedLocations]

  // Provide an explicit mongo format here to deal with the sub-document root
  val root = "location-preferences"
  val mongoFormat: Format[SelectedLocations] = (
    (__ \ root \ "locations").format[List[LocationId]] and
      (__ \ root \ "interests").format[List[String]]
  )(SelectedLocations.apply, unlift(o => Some(Tuple.fromProductTyped(o))))
}
