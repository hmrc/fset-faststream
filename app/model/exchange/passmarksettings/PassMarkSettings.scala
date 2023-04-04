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

package model.exchange.passmarksettings

import model.SchemeId
import play.api.libs.json.Json

import java.time.{Instant, OffsetDateTime}

trait PassMarkSettings {
  def schemes: List[PassMark]
  def version: String
  def createDate: Instant
  def createdBy: String
}

case class Phase1PassMarkSettings(
                                   schemes: List[Phase1PassMark],
                                   version: String,
                                   createDate: Instant,
                                   createdBy: String
) extends PassMarkSettings

object Phase1PassMarkSettings {
  // Do not remove this as it is needed to serialize the date as epoch millis
  //import model.persisted.Play25DateCompatibility.epochMillisDateFormat
  import model.persisted.Play25DateCompatibility.javaTimeInstantEpochMillisDateFormat
  implicit val jsonFormat = Json.format[Phase1PassMarkSettings]

  def merge(oldPassMarkSettings: Option[Phase1PassMarkSettings],
            newPassMarkSettings: Phase1PassMarkSettings): Phase1PassMarkSettings = {
    def toMap(passmark: Phase1PassMarkSettings) = passmark.schemes.groupBy(_.schemeId).view.mapValues { v =>
      require(v.size == 1, s"Scheme name must be non empty and must be unique: ${v.mkString(",")}")
      v.head
    }.toMap
    def toSchemeNames(passmark: Phase1PassMarkSettings) = passmark.schemes.map(_.schemeId)

    oldPassMarkSettings match {
      case Some(latest) =>
        val currentPassMarkSettingsMap = toMap(latest)
        val newestPassMarkSettingsMap = toMap(newPassMarkSettings)
        val uniqueSchemesInOrder = (oldPassMarkSettings.map(toSchemeNames)
          .getOrElse(Nil) ++ toSchemeNames(newPassMarkSettings)).distinct
        val mergedPassMarkSettings = mergeToListInOrder(currentPassMarkSettingsMap, newestPassMarkSettingsMap,
          uniqueSchemesInOrder)
        newPassMarkSettings.copy(schemes = mergedPassMarkSettings)
      case None =>
        newPassMarkSettings
    }
  }

  private def mergeToListInOrder[T](originalMap: Map[SchemeId, T], toUpdateMap: Map[SchemeId, T],
                                               order: List[SchemeId]): List[T] = {
    val mergedMaps = originalMap ++ toUpdateMap
    order.map(schemeName => mergedMaps(schemeName))
  }
}

case class Phase2PassMarkSettings(
  schemes: List[Phase2PassMark],
  version: String,
  createDate: Instant,
  createdBy: String
) extends PassMarkSettings

object Phase2PassMarkSettings {
  // Do not remove this as it is needed to serialize the date as epoch millis
  import model.persisted.Play25DateCompatibility.epochMillisDateFormat
  implicit val jsonFormat = Json.format[Phase2PassMarkSettings]
}

case class Phase3PassMarkSettings(
  schemes: List[Phase3PassMark],
  version: String,
  createDate: Instant,
  createdBy: String
) extends PassMarkSettings

object Phase3PassMarkSettings {
  // Do not remove this as it is needed to serialize the date as epoch millis
  import model.persisted.Play25DateCompatibility.epochMillisDateFormat
  implicit val jsonFormat = Json.format[Phase3PassMarkSettings]
}

case class AssessmentCentrePassMarkSettings(
                                   schemes: List[AssessmentCentrePassMark],
                                   version: String,
                                   createDate: Instant,
                                   createdBy: String
                                 ) extends PassMarkSettings {
  // Only display pass marks for the Commercial scheme to reduce the amount we log
  def abbreviated = s"schemes=${schemes.filter(s => s.schemeId == SchemeId("Commercial"))},<<truncated>>" +
    s"version=$version,createDate=$createDate,createdBy=$createdBy"
}

object AssessmentCentrePassMarkSettings {
  // Do not remove this as it is needed to serialize the date as epoch millis
  import model.persisted.Play25DateCompatibility.epochMillisDateFormat
  implicit val jsonFormat = Json.format[AssessmentCentrePassMarkSettings]
}
