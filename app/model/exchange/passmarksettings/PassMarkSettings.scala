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

package model.exchange.passmarksettings

import model.SchemeId
import org.joda.time.DateTime
import play.api.libs.json.Json
import reactivemongo.bson.Macros

trait PassMarkSettings {
  def schemes: List[PassMark]
  def version: String
  def createDate: DateTime
  def createdBy: String
}

case class Phase1PassMarkSettings(
  schemes: List[Phase1PassMark],
  version: String,
  createDate: DateTime,
  createdBy: String
) extends PassMarkSettings

object Phase1PassMarkSettings {
  import repositories.BSONDateTimeHandler
  implicit val jsonFormat = Json.format[Phase1PassMarkSettings]
  implicit val bsonHandler = Macros.handler[Phase1PassMarkSettings]

  def merge(oldPassMarkSettings: Option[Phase1PassMarkSettings],
            newPassMarkSettings: Phase1PassMarkSettings): Phase1PassMarkSettings = {
    def toMap(passmark: Phase1PassMarkSettings) = passmark.schemes.groupBy(_.schemeId).mapValues { v =>
      require(v.size == 1, s"Scheme name must be non empty and must be unique: ${v.mkString(",")}")
      v.head
    }
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
  createDate: DateTime,
  createdBy: String
) extends PassMarkSettings

object Phase2PassMarkSettings {
  import repositories.BSONDateTimeHandler
  implicit val jsonFormat = Json.format[Phase2PassMarkSettings]
  implicit val bsonHandler = Macros.handler[Phase2PassMarkSettings]
}

case class Phase3PassMarkSettings(
  schemes: List[Phase3PassMark],
  version: String,
  createDate: DateTime,
  createdBy: String
) extends PassMarkSettings

object Phase3PassMarkSettings {
  import repositories.BSONDateTimeHandler
  implicit val jsonFormat = Json.format[Phase3PassMarkSettings]
  implicit val bsonHandler = Macros.handler[Phase3PassMarkSettings]
}

case class AssessmentCentrePassMarkSettings(
                                   schemes: List[AssessmentCentrePassMark],
                                   version: String,
                                   createDate: DateTime,
                                   createdBy: String
                                 ) extends PassMarkSettings

object AssessmentCentrePassMarkSettings {
  import repositories.BSONDateTimeHandler
  implicit val jsonFormat = Json.format[AssessmentCentrePassMarkSettings]
  implicit val bsonHandler = Macros.handler[AssessmentCentrePassMarkSettings]
}
