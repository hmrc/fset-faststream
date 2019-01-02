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

package model.persisted

import play.api.libs.json.Json
import repositories.csv.FSACIndicatorCSVRepository
import reactivemongo.bson.{BSONDocument, BSONHandler, Macros}

case class FSACIndicator(area: String, assessmentCentre: String, version: String)

object FSACIndicator {
  implicit val jsonFormat = Json.format[FSACIndicator]
  implicit val bsonFormat = Macros.handler[FSACIndicator]

  def apply(indicator: model.FSACIndicator): FSACIndicator = {
    FSACIndicator(indicator.area, indicator.assessmentCentre,
      FSACIndicatorCSVRepository.FSACIndicatorVersion)
  }
}
