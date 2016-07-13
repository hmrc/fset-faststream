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

package repositories

import java.util.UUID

import connectors.PassMarkExchangeObjects
import connectors.PassMarkExchangeObjects.{ Scheme, SchemeThreshold, SchemeThresholds, Settings }
import model.Commands._
import org.joda.time.DateTime
import play.api.libs.json.{ JsNumber, JsObject }
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PassMarkSettingsRepository {
  def create(settings: Settings, schemeNames: List[String]): Future[PassMarkSettingsCreateResponse]

  def tryGetLatestVersion(schemeNames: List[String]): Future[Option[Settings]]
}

class PassMarkSettingsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Settings, BSONObjectID]("pass-mark-settings", mongo,
    PassMarkExchangeObjects.Implicits.passMarkSettingsFormat, ReactiveMongoFormats.objectIdFormats) with PassMarkSettingsRepository {

  override def create(settings: Settings, schemeNames: List[String]): Future[PassMarkSettingsCreateResponse] = {

    def schemesToFieldMappings(schemes: List[Scheme]): List[(String, BSONValue)] = {
      schemes.flatMap(scheme => {
        val schemeThresholds = scheme.schemeThresholds
        val fixedMappings = List(
          s"schemes.${scheme.schemeName}.competency.pass" -> BSONDouble(schemeThresholds.competency.passThreshold),
          s"schemes.${scheme.schemeName}.competency.fail" -> BSONDouble(schemeThresholds.competency.failThreshold),
          s"schemes.${scheme.schemeName}.verbal.pass" -> BSONDouble(schemeThresholds.verbal.passThreshold),
          s"schemes.${scheme.schemeName}.verbal.fail" -> BSONDouble(schemeThresholds.verbal.failThreshold),
          s"schemes.${scheme.schemeName}.numerical.pass" -> BSONDouble(schemeThresholds.numerical.passThreshold),
          s"schemes.${scheme.schemeName}.numerical.fail" -> BSONDouble(schemeThresholds.numerical.failThreshold),
          s"schemes.${scheme.schemeName}.situational.pass" -> BSONDouble(schemeThresholds.situational.passThreshold),
          s"schemes.${scheme.schemeName}.situational.fail" -> BSONDouble(schemeThresholds.situational.failThreshold)
        )

        schemeThresholds.combination match {
          case Some(thresholds) => fixedMappings ++ List(
            s"schemes.${scheme.schemeName}.combination.pass" -> BSONDouble(schemeThresholds.combination.get.passThreshold),
            s"schemes.${scheme.schemeName}.combination.fail" -> BSONDouble(schemeThresholds.combination.get.failThreshold)
          )
          case _ => fixedMappings
        }
      })
    }

    val fieldMappings = schemesToFieldMappings(settings.schemes)

    val baseBSON = BSONDocument(
      "version" -> UUID.randomUUID().toString,
      "createDate" -> BSONDateTime(settings.createDate.getMillis),
      "createdByUser" -> settings.createdByUser,
      "setting" -> settings.setting
    )

    val settingsBSON = fieldMappings.foldLeft(baseBSON)(_ ++ _)

    collection.insert(settingsBSON) flatMap { _ =>
      tryGetLatestVersion(schemeNames).map(createResponse =>
        PassMarkSettingsCreateResponse(createResponse.get.version, createResponse.get.createDate))
    }
  }

  override def tryGetLatestVersion(schemeNames: List[String]): Future[Option[Settings]] = {

    val query = BSONDocument()
    val sort = new JsObject(Seq("createDate" -> JsNumber(-1)))

    collection.find(query).sort(sort).one[BSONDocument] map { docOpt =>
      docOpt.map(constructPassMarkSettingsFromBSONDocument(_, schemeNames))
    }
  }

  def constructPassMarkSettingsFromBSONDocument(doc: BSONDocument, schemeNames: List[String]): Settings =
    Settings(
      schemes = schemeNames.map { schemeName =>
        Scheme(
          schemeName,
          schemeThresholds =
            SchemeThresholds(
              competency = constructThreshold(doc, schemeName, "competency"),
              verbal = constructThreshold(doc, schemeName, "verbal"),
              numerical = constructThreshold(doc, schemeName, "numerical"),
              situational = constructThreshold(doc, schemeName, "situational"),
              combination = (
                doc.getAs[Double](s"schemes.$schemeName.combination.fail"),
                doc.getAs[Double](s"schemes.$schemeName.combination.pass")
              ) match {
                  case (Some(fail), Some(pass)) =>
                    Some(SchemeThreshold(
                      fail,
                      pass
                    ))
                  case _ => None
                }
            )
        )
      },
      version = doc.getAs[String]("version").get,
      createDate = doc.getAs[DateTime]("createDate").get,
      createdByUser = doc.getAs[String]("createdByUser").get,
      setting = doc.getAs[String]("setting").get
    )

  def constructThreshold(doc: BSONDocument, schemeName: String, testType: String) = {
    SchemeThreshold(
      doc.getAs[Double](s"schemes.$schemeName.$testType.fail").get,
      doc.getAs[Double](s"schemes.$schemeName.$testType.pass").get
    )
  }

}
