/*
 * Copyright 2024 HM Revenue & Customs
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

import factories.ITDateTimeFactoryMock
import model.ApplicationStatus.*
import model.Exceptions.{CannotUpdateLocationPreferences, LocationPreferencesNotFound}
import model.{ApplicationRoute, LocationId, Schemes, SelectedLocations}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{MongoCollection, SingleObservableFuture}
import repositories.CollectionNames
import repositories.application.GeneralApplicationMongoRepository
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

class LocationPreferencesRepositorySpec extends MongoRepositorySpec with Schemes {

  val collectionName: String = CollectionNames.APPLICATION

  def repository = new LocationPreferencesMongoRepository(mongo)
  def applicationRepository = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)
  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)
  def insert(doc: Document) = applicationCollection.insertOne(doc).toFuture()

  val twoLocations = SelectedLocations(List(LocationId("location1"), LocationId("location2")), List("Interest 1"))

  "save and find" should {
    "save and return location preferences" in {
      val (persistedLocations, application) = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "testAccountId" -> TestAccountId,
          "applicationStatus" -> Codecs.toBson(CREATED), "frameworkId" -> FrameworkId,
          "applicationRoute" -> Codecs.toBson(ApplicationRoute.Sdip)))
        _ <- repository.save(AppId, twoLocations)
        appResponse <- applicationRepository.findByUserId(UserId, FrameworkId)
        locations <- repository.find(AppId)
      } yield (locations, appResponse)).futureValue

      persistedLocations mustBe twoLocations
      application.progressResponse.locationPreferences mustBe true
    }

    "return an exception when application does not exist" in {
      val exception = (for {
        _ <- repository.save(AppId, twoLocations)
        locations <- repository.find(AppId)
      } yield locations).failed.futureValue

      exception mustBe CannotUpdateLocationPreferences(AppId)
    }
  }

  "find" should {
    "return an exception when location-preferences does not exist" in {
      val exception = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> Codecs.toBson(CREATED)))
        _ <- repository.find(AppId)
      } yield ()).failed.futureValue

      exception mustBe LocationPreferencesNotFound(AppId)
    }
  }
}
