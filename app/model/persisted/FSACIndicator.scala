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

import org.joda.time.LocalDate
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, __}

case class FSACIndicator(area: String, assessmentCentre: String, version: String)

object FSACIndicator {
  implicit val jsonFormat = Json.format[FSACIndicator]

  // Provide an explicit mongo format here to deal with the sub-document root
  // This data lives in the application collection
  val root = "fsac-indicator"
  val mongoFormat: Format[FSACIndicator] = (
    (__ \ root \ "area").format[String] and
      (__ \ root \ "assessmentCentre").format[String] and
      (__ \ root \ "version").format[String]
    )(FSACIndicator.apply, unlift(FSACIndicator.unapply))

  def apply(indicator: model.FSACIndicator, fsacIndicatorVersion: String): FSACIndicator = {
    FSACIndicator(indicator.area, indicator.assessmentCentre, fsacIndicatorVersion)
  }
}
