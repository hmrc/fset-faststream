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

package repositories.schemepreferences

import javax.inject.{Inject, Singleton}
import model.Exceptions.{CannotUpdateSchemePreferences, SchemePreferencesNotFound}
import model.{SchemeId, SelectedSchemes}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{Filters, Projections}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import repositories.{CollectionNames, ReactiveRepositoryHelpers}

import scala.concurrent.{ExecutionContext, Future}

trait SchemePreferencesRepository {
  def find(applicationId: String): Future[SelectedSchemes]
  def save(applicationId: String, schemePreferences: SelectedSchemes): Future[Unit]
  def add(applicationId: String, newScheme: SchemeId): Future[Unit]
}

@Singleton
class SchemePreferencesMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[SelectedSchemes](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = SelectedSchemes.mongoFormat,
    indexes = Nil
  ) with SchemePreferencesRepository with ReactiveRepositoryHelpers {

  private val SchemePreferencesDocumentKey = "scheme-preferences"

  def find(applicationId: String): Future[SelectedSchemes] = {
    val query = Filters.and(Filters.equal("applicationId", applicationId), Filters.exists(SchemePreferencesDocumentKey))
    val projection = Projections.include(SchemePreferencesDocumentKey)

    collection.find(query).projection(projection).headOption() map {
      case Some(selectedSchemes) => selectedSchemes
      case _ => throw SchemePreferencesNotFound(applicationId)
    }
  }

  def save(applicationId: String, schemePreference: SelectedSchemes): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$set" -> Document(
      SchemePreferencesDocumentKey -> Codecs.toBson(schemePreference),
      "progress-status." + SchemePreferencesDocumentKey -> true
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "saving scheme preferences",
      CannotUpdateSchemePreferences(applicationId))

    collection.updateOne(query, update).toFuture() map validator
  }

  def add(applicationId: String, newScheme: SchemeId): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document(
      "$addToSet" -> Document(
        s"scheme-preferences.schemes" -> Codecs.toBson(newScheme)
      )
    )

    val validator = singleUpdateValidator(applicationId, actionDesc = s"inserting $newScheme scheme")

    collection.updateOne(query, update).toFuture() map validator
  }
}
