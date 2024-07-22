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

package repositories.locationpreferences

import model.Exceptions.{CannotUpdateLocationPreferences, LocationPreferencesNotFound}
import model.SelectedLocations
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{Filters, Projections}
import repositories.{CollectionNames, ReactiveRepositoryHelpers}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait LocationPreferencesRepository {
  def find(applicationId: String): Future[SelectedLocations]
  def save(applicationId: String, locationPreferences: SelectedLocations): Future[Unit]
}

@Singleton
class LocationPreferencesMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[SelectedLocations](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = SelectedLocations.mongoFormat,
    indexes = Nil
  ) with LocationPreferencesRepository with ReactiveRepositoryHelpers {

  private val LocationPreferencesDocumentKey = "location-preferences"

  override def find(applicationId: String): Future[SelectedLocations] = {
    val query = Filters.and(Filters.equal("applicationId", applicationId), Filters.exists(LocationPreferencesDocumentKey))
    val projection = Projections.include(LocationPreferencesDocumentKey)

    collection.find(query).projection(projection).headOption() map {
      case Some(selectedLocations) => selectedLocations
      case _ => throw LocationPreferencesNotFound(applicationId)
    }
  }

  override def save(applicationId: String, locationPreferences: SelectedLocations): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$set" -> Document(
      LocationPreferencesDocumentKey -> Codecs.toBson(locationPreferences),
      "progress-status." + LocationPreferencesDocumentKey -> true
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "saving location preferences",
      CannotUpdateLocationPreferences(applicationId))

    collection.updateOne(query, update).toFuture() map validator
  }
}
