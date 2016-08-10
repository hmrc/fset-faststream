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

package repositories.schemepreferences

import model.Exceptions.{CannotUpdateSchemePreferences, SchemePreferencesNotFound}
import model.SelectedSchemes
import reactivemongo.api.DB
import reactivemongo.bson.{BSONDocument, BSONObjectID, _}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SchemePreferencesRepository {
  def find(applicationId: String): Future[SelectedSchemes]

  def save(applicationId: String, schemePreferences: SelectedSchemes): Future[Unit]
}

class SchemePreferencesMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[SelectedSchemes, BSONObjectID]("application", mongo,
    SelectedSchemes.selectedSchemesFormat, ReactiveMongoFormats.objectIdFormats) with SchemePreferencesRepository {
  private val SchemePreferencesDocumentKey = "scheme-preferences"

  def find(applicationId: String): Future[SelectedSchemes] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(SchemePreferencesDocumentKey -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](SchemePreferencesDocumentKey).isDefined =>
        document.getAs[SelectedSchemes](SchemePreferencesDocumentKey).get
      case _ => throw SchemePreferencesNotFound(applicationId)
    }
  }

  def save(applicationId: String, schemePreference: SelectedSchemes): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val preferencesBSON = BSONDocument("$set" -> BSONDocument(
      SchemePreferencesDocumentKey -> schemePreference,
      "progress-status." + SchemePreferencesDocumentKey -> true
    ))
    collection.update(query, preferencesBSON, upsert = false) map {
      case lastError if lastError.nModified == 0 && lastError.n == 0 =>
        logger.error(
          s"""Failed to write scheme preferences for application Id:
             | $applicationId -> ${lastError.writeConcernError.map(_.errmsg).mkString(",")}""".stripMargin)
        throw CannotUpdateSchemePreferences(applicationId)
      case _ => ()
    }
  }
}
