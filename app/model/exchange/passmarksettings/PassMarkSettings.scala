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

import model.{SchemeId, Schemes}
import play.api.libs.json.OFormat

import java.time.OffsetDateTime
import play.api.libs.json.Json

trait PassMarkSettings {
  def schemes: List[PassMark]
  def version: String
  def createDate: OffsetDateTime
  def createdBy: String
  def toPersistence: PassMarkSettingsPersistence
}

trait PassMarkSettingsPersistence {
  def schemes: List[PassMark]
  def version: String
  def createDate: OffsetDateTime
  def createdBy: String
  def toExchange: PassMarkSettings
}

case class Phase1PassMarkSettingsPersistence(
  schemes: List[Phase1PassMark],
  version: String,
  createDate: OffsetDateTime,
  createdBy: String
) extends PassMarkSettingsPersistence {
  override def toExchange = Phase1PassMarkSettings(schemes, version, createDate, createdBy)
}

object Phase1PassMarkSettingsPersistence {
  import repositories.formats.MongoJavatimeFormats.Implicits._ // Needed to handle storing ISODate format in Mongo
  implicit val jsonFormat: OFormat[Phase1PassMarkSettingsPersistence] = Json.format[Phase1PassMarkSettingsPersistence]
}

case class Phase1PassMarkSettings(
  schemes: List[Phase1PassMark],
  version: String,
  createDate: OffsetDateTime,
  createdBy: String
) extends PassMarkSettings {
  override def toPersistence = Phase1PassMarkSettingsPersistence(schemes, version, createDate, createdBy)
}

object Phase1PassMarkSettings {
  implicit val jsonFormat: OFormat[Phase1PassMarkSettings] = Json.format[Phase1PassMarkSettings]

  def merge(oldPassMarkSettings: Option[Phase1PassMarkSettingsPersistence],
            newPassMarkSettings: Phase1PassMarkSettingsPersistence): Phase1PassMarkSettingsPersistence = {
    def toMap(passmark: Phase1PassMarkSettingsPersistence) = passmark.schemes.groupBy(_.schemeId).view.mapValues { v =>
      require(v.size == 1, s"Scheme name must be non empty and must be unique: ${v.mkString(",")}")
      v.head
    }.toMap
    def toSchemeNames(passmark: Phase1PassMarkSettingsPersistence) = passmark.schemes.map(_.schemeId)

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
  createDate: OffsetDateTime,
  createdBy: String
) extends PassMarkSettings {
  override def toPersistence = Phase2PassMarkSettingsPersistence(schemes, version, createDate, createdBy)
}

object Phase2PassMarkSettings {
  implicit val jsonFormat: OFormat[Phase2PassMarkSettings] = Json.format[Phase2PassMarkSettings]
}

case class Phase2PassMarkSettingsPersistence(
                                   schemes: List[Phase2PassMark],
                                   version: String,
                                   createDate: OffsetDateTime,
                                   createdBy: String
                                 ) extends PassMarkSettingsPersistence {
  override def toExchange = Phase2PassMarkSettings(schemes, version, createDate, createdBy)
}

object Phase2PassMarkSettingsPersistence {
  import repositories.formats.MongoJavatimeFormats.Implicits._ // Needed to handle storing ISODate format in Mongo
  implicit val jsonFormat: OFormat[Phase2PassMarkSettingsPersistence] = Json.format[Phase2PassMarkSettingsPersistence]
}

case class Phase3PassMarkSettings(
  schemes: List[Phase3PassMark],
  version: String,
  createDate: OffsetDateTime,
  createdBy: String
) extends PassMarkSettings {
  override def toPersistence = Phase3PassMarkSettingsPersistence(schemes, version, createDate, createdBy)
}

object Phase3PassMarkSettings {
  implicit val jsonFormat: OFormat[Phase3PassMarkSettings] = Json.format[Phase3PassMarkSettings]
}

case class Phase3PassMarkSettingsPersistence(
                                              schemes: List[Phase3PassMark],
                                              version: String,
                                              createDate: OffsetDateTime,
                                              createdBy: String
                                            ) extends PassMarkSettingsPersistence {
  override def toExchange = Phase3PassMarkSettings(schemes, version, createDate, createdBy)
}

object Phase3PassMarkSettingsPersistence {
  import repositories.formats.MongoJavatimeFormats.Implicits._ // Needed to handle storing ISODate format in Mongo
  implicit val jsonFormat: OFormat[Phase3PassMarkSettingsPersistence] = Json.format[Phase3PassMarkSettingsPersistence]
}

// This class is now based on exercise pass marks not competency pass marks
case class AssessmentCentrePassMarkSettings(
                                             schemes: List[AssessmentCentreExercisePassMark],
                                             version: String,
                                             createDate: OffsetDateTime,
                                             createdBy: String
                                 ) extends PassMarkSettings {
  override def toPersistence = AssessmentCentrePassMarkSettingsPersistence(schemes, version, createDate, createdBy)
}

object AssessmentCentrePassMarkSettings {
  implicit val jsonFormat: OFormat[AssessmentCentrePassMarkSettings] = Json.format[AssessmentCentrePassMarkSettings]
}

case class AssessmentCentrePassMarkSettingsPersistence(
                                              schemes: List[AssessmentCentreExercisePassMark],
                                              version: String,
                                              createDate: OffsetDateTime,
                                              createdBy: String
                                            ) extends PassMarkSettingsPersistence with Schemes {
  // Only display pass marks for the Commercial scheme to reduce the amount we log
  def abbreviated = s"schemes=${schemes.filter(s => s.schemeId == Commercial)},<<truncated>>" +
    s"version=$version,createDate=$createDate,createdBy=$createdBy"

  override def toExchange = AssessmentCentrePassMarkSettings(schemes, version, createDate, createdBy)
}

object AssessmentCentrePassMarkSettingsPersistence {
  import repositories.formats.MongoJavatimeFormats.Implicits._ // Needed to handle storing ISODate format in Mongo
  implicit val jsonFormat: OFormat[AssessmentCentrePassMarkSettingsPersistence] = Json.format[AssessmentCentrePassMarkSettingsPersistence]
}
