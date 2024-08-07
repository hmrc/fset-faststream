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

package repositories.personaldetails

import factories.ITDateTimeFactoryMock
import model.ApplicationStatus._
import model.Exceptions.PersonalDetailsNotFound
import model.persisted.PersonalDetailsExamples._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import repositories.application.GeneralApplicationMongoRepository
import testkit.MongoRepositorySpec

class PersonalDetailsRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.APPLICATION

  def repository = new PersonalDetailsMongoRepository(ITDateTimeFactoryMock, mongo)
  def appRepository = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)
  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)
  def insert(doc: Document) = applicationCollection.insertOne(doc).toFuture()

  "update candidate" should {
    "modify the details and find the personal details successfully" in {
      val personalDetails = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED.toBson))
        _ <- repository.update(AppId, UserId, JohnDoe, List(CREATED), IN_PROGRESS)
        pd <- repository.find(AppId)
      } yield pd).futureValue

      val applicationStatus = appRepository.findStatus(AppId).futureValue

      personalDetails mustBe JohnDoe
      applicationStatus.status mustBe IN_PROGRESS
//      timesApproximatelyEqual(applicationStatus.statusDate.get, DateTime.now()) mustBe true
    }

    "do not update the application in different status than required" in {
      val actualException = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> SUBMITTED.toBson))
        _ <- repository.update(AppId, UserId, JohnDoe, List(CREATED), IN_PROGRESS)
        pd <- repository.find(AppId)
      } yield pd).failed.futureValue

      actualException mustBe PersonalDetailsNotFound(AppId)
    }

    "modify the details and find the personal details successfully without changing application status" in {
      val personalDetails = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> SUBMITTED.toBson))
        _ <- repository.updateWithoutStatusChange(AppId, UserId, JohnDoe)
        pd <- repository.find(AppId)
      } yield pd).futureValue

      personalDetails mustBe JohnDoe
    }
  }

  "find candidate" should {
    "throw an exception when the candidate does not exist" in {
      val result = repository.find(AppId).failed.futureValue
      result mustBe PersonalDetailsNotFound(AppId)
    }
  }

  "find by ids" should {
    "fetch personal details data when there is some" in {
      val personalDetails = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED.toBson))
        _ <- repository.update(AppId, UserId, JohnDoe, List(CREATED), IN_PROGRESS)
        pd <- repository.findByIds(Seq(AppId))
      } yield pd).futureValue

      personalDetails must contain theSameElementsAs Seq(AppId -> Some(JohnDoe))
    }

    "fetch no personal details data when there is none" in {
      val personalDetails = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED.toBson))
        pd <- repository.findByIds(Seq(AppId))
      } yield pd).futureValue

      personalDetails must contain theSameElementsAs Seq(AppId -> None)
    }
  }
}
