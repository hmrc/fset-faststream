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

package repositories.schemepreferences

import factories.ITDateTimeFactoryMock
import model.ApplicationStatus._
import model.Exceptions.{CannotUpdateSchemePreferences, SchemePreferencesNotFound}
import model.SelectedSchemesExamples._
import model.{ApplicationRoute, SchemeId, Schemes}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import repositories.application.GeneralApplicationMongoRepository
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

class SchemePreferencesRepositorySpec extends MongoRepositorySpec with Schemes {

  val collectionName: String = CollectionNames.APPLICATION

  def repository = new SchemePreferencesMongoRepository(mongo)
  def applicationRepository = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)
  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)
  def insert(doc: Document) = applicationCollection.insertOne(doc).toFuture()

  "save and find" should {
    "save and return scheme preferences" in {
      val (persistedSchemes, application) = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "testAccountId" -> TestAccountId,
          "applicationStatus" -> Codecs.toBson(CREATED), "frameworkId" -> FrameworkId,
          "applicationRoute" -> Codecs.toBson(ApplicationRoute.Faststream)))
        _ <- repository.save(AppId, twoSchemes)
        appResponse <- applicationRepository.findByUserId(UserId, FrameworkId)
        schemes <- repository.find(AppId)
      } yield (schemes, appResponse)).futureValue

      persistedSchemes mustBe twoSchemes
      application.progressResponse.schemePreferences mustBe true
    }

    "return an exception when application does not exist" in {
      val exception = (for {
        _ <- repository.save(AppId, twoSchemes)
        schemes <- repository.find(AppId)
      } yield schemes).failed.futureValue

      exception mustBe CannotUpdateSchemePreferences(AppId)
    }
  }

  "find" should {
    "return an exception when scheme-preferences does not exist" in {
      val exception = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> Codecs.toBson(CREATED)))
        _ <- repository.find(AppId)
      } yield ()).failed.futureValue

      exception mustBe SchemePreferencesNotFound(AppId)
    }
  }

  "add scheme" should {
    "add a new scheme to the application" in {
      val actualSchemes = createTwoSchemes()
      repository.add(AppId, Sdip).futureValue

      val schemes = repository.find(AppId).futureValue
      schemes.schemes.size mustBe (actualSchemes.size + 1)
      schemes.schemes must contain theSameElementsAs (Sdip :: actualSchemes)
    }

    "not add duplicates" in {
      val actualSchemes = createTwoSchemes()
      repository.add(AppId, Sdip).futureValue
      repository.add(AppId, Sdip).futureValue

      val schemes = repository.find(AppId).futureValue
      schemes.schemes.size mustBe (actualSchemes.size + 1)
      schemes.schemes must contain theSameElementsAs (Sdip :: actualSchemes)
    }
  }

  private def createTwoSchemes(): List[SchemeId] = {
    insert(Document("applicationId" -> AppId, "userId" -> UserId,
      "applicationStatus" -> Codecs.toBson(CREATED), "frameworkId" -> FrameworkId)).futureValue
    repository.save(AppId, twoSchemes).futureValue
    twoSchemes.schemes
  }
}
