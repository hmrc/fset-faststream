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

package connectors

import org.joda.time.DateTime
import play.api.libs.json.Json

object PassMarkExchangeObjects {

  case class SettingsResponse(
    schemes: List[SchemeResponse],
    createDate: Option[DateTime],
    createdByUser: Option[String],
    setting: String
  )

  case class SchemeResponse(schemeName: String, schemeThresholds: Option[SchemeThresholds])

  case class SettingsCreateRequest(
    schemes: List[Scheme],
    createDate: DateTime,
    createdByUser: String,
    setting: String
  )

  case class Settings(
    schemes: List[Scheme],
    version: String,
    createDate: DateTime,
    createdByUser: String,
    setting: String
  )

  case class Scheme(schemeName: String, schemeThresholds: SchemeThresholds)

  case class SchemeThresholds(
    competency: SchemeThreshold,
    verbal: SchemeThreshold,
    numerical: SchemeThreshold,
    situational: SchemeThreshold,
    combination: Option[SchemeThreshold]
  )

  case class SchemeThreshold(failThreshold: Double, passThreshold: Double)

  object Implicits {
    implicit val passMarkSchemeThreshold = Json.format[SchemeThreshold]
    implicit val passMarkSchemeThresholds = Json.format[SchemeThresholds]
    implicit val passMarkSchemeFormat = Json.format[Scheme]
    implicit val passMarkSettingsFormat = Json.format[Settings]

    implicit val passMarkSettingsCreateRequestFormat = Json.format[SettingsCreateRequest]

    implicit val passMarkSchemeResponseFormat = Json.format[SchemeResponse]
    implicit val passMarkSettingsResponseFormat = Json.format[SettingsResponse]
  }
}
