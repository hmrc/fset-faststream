/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import model.Exceptions.{ CannotUpdateSchemePreferences, SchemePreferencesNotFound }
import model.{ SchemeId, SelectedSchemes }
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.{ BSONDocument, BSONObjectID, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.{ CollectionNames, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SchemePreferencesRepository {
  def find(applicationId: String): Future[SelectedSchemes]

  def save(applicationId: String, schemePreferences: SelectedSchemes): Future[Unit]

  def add(applicationId: String, newScheme: SchemeId): Future[Unit]
}

@Singleton
class SchemePreferencesMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[SelectedSchemes, BSONObjectID](
    CollectionNames.APPLICATION,
    mongoComponent.mongoConnector.db,
    SelectedSchemes.selectedSchemesFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with SchemePreferencesRepository with ReactiveRepositoryHelpers {

  private val SchemePreferencesDocumentKey = "scheme-preferences"

  def find(applicationId: String): Future[SelectedSchemes] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(SchemePreferencesDocumentKey -> 1, "_id" -> 0)

    collection.find(query, Some(projection)).one[BSONDocument] map {
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

    val validator = singleUpdateValidator(applicationId, actionDesc = "saving scheme preferences",
      CannotUpdateSchemePreferences(applicationId))

    collection.update(ordered = false).one(query, preferencesBSON) map validator
  }

  def add(applicationId: String, newScheme: SchemeId): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument(
      "$addToSet" -> BSONDocument(
        s"scheme-preferences.schemes" -> newScheme
      )
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = s"inserting sdip scheme")

    collection.update(ordered = false).one(query, update) map validator
  }
}
