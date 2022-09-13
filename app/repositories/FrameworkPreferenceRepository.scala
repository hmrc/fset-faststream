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

package repositories

import javax.inject.{Inject, Singleton}
import model.Preferences
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{Filters, Projections}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FrameworkPreferenceRepository {
  def savePreferences(applicationId: String, preferences: Preferences): Future[Unit]
  def tryGetPreferences(applicationId: String): Future[Option[Preferences]]
}

// TODO: mongo this class looks like old code and should be removed
// TODO: it is only called when the candidate submits the application
@Singleton
class FrameworkPreferenceMongoRepository @Inject() (mongoComponent: MongoComponent)
  extends PlayMongoRepository[Preferences](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongoComponent,
    domainFormat = Preferences.jsonFormat,
    indexes = Nil
  ) with FrameworkPreferenceRepository with ReactiveRepositoryHelpers {

  /*
  override def savePreferences(applicationId: String, preferences: Preferences): Future[Unit] = {
    require(preferences.isValid, "Preferences must be valid when saving to repository")

    val query = BSONDocument("applicationId" -> applicationId)
    val preferencesBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> "IN_PROGRESS",
      "progress-status.frameworks-location" -> preferences.alternatives.isDefined,
      "framework-preferences" -> preferences
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "deleting allocation")

    collection.update(ordered = false).one(query, preferencesBSON) map validator
  }*/
  override def savePreferences(applicationId: String, preferences: Preferences): Future[Unit] = ???

  override def tryGetPreferences(applicationId: String): Future[Option[Preferences]] = {
    val frameworkPreferencesDocumentKey = "framework-preferences"
    val query = Filters.and(
      Filters.equal("applicationId", applicationId),
      Filters.exists(frameworkPreferencesDocumentKey)
    )

    val projection = Projections.include(frameworkPreferencesDocumentKey)
    collection.find(query).projection(projection).headOption()
  }
}
