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

package repositories

import model.Exceptions.{CannotUpdateRecord, NotFoundException}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{MongoCollection, SingleObservableFuture}
import play.api.libs.json.{Json, OFormat}
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

class ReactiveRepositoryHelpersSpec extends MongoRepositorySpec {

  "Update Validator" should {
    "return Unit when successfully updating a document" in new TestFixture {
      repo.insert(testType1).futureValue
      val result = repo.update(testType2) map repo.singleUpdateValidator("id", s"updating single document in $collectionName collection")
      result.futureValue mustBe unit
    }
    "throw an exception when update does not affect any documents" in new TestFixture {
      val result = repo.update(testType1) map repo.singleUpdateValidator("id", s"updating single document in $collectionName collection")
      result.failed.futureValue mustBe a[CannotUpdateRecord]
    }
    "return Unit when update does not affect any documents, and ignoreNotFound enabled" in new TestFixture {
      val result = repo.update(testType1) map repo.singleUpdateValidator("id", "testing", ignoreNotFound = true)
      result.futureValue mustBe unit
    }
    "throw custom exception when update does not affect any documents and custom exception provided" ignore new TestFixture {
      class TestException extends Exception
      val result = repo.update(testType1) map repo.singleUpdateValidator("id", "testing", new TestException)
      result.failed.futureValue mustBe a[TestException]
    }
  }

  "Upsert Validator" should {
    "return Unit when successfully inserting a document" in new TestFixture {
      val result = repo.update(testType1) map repo.singleUpsertValidator("id", "testing")
      result.futureValue mustBe unit
    }
    "return Unit when successfully upserting a document" in new TestFixture {
      repo.insert(testType1).futureValue
      val result = repo.update(testType1) map repo.singleUpsertValidator("id", "testing")
      result.futureValue mustBe unit
    }
  }

  "Delete Validator" should {
    "return Unit when successfully removing a document" in new TestFixture {
      repo.insert(testType1).futureValue
      val result = repo.remove("testType1") map repo.singleRemovalValidator("id", "testing")
      result.futureValue mustBe unit
    }
    "throw NotFoundException when removing a non-existent document" in new TestFixture {
      repo.insert(testType1).futureValue
      val result = repo.remove("wrong-id") map repo.singleRemovalValidator("id", "testing")
      result.failed.futureValue mustBe a[NotFoundException]
    }
  }

  /*
  Will generate this json structure in mongo:
  {
      "_id" : ObjectId("6287ae4852e2f30f0d83e2ca"),
      "id" : { "testType" : "testType1" }
  }
  */

  val id = "id"
  val testType1 = id -> TestType("testType1")
  val testType2 = id -> TestType("testType2")

  trait TestFixture {
    val repo = new TestRepository(mongo)
  }

  val collectionNameForTest = "reactive-repository-helpers-test"

  override val collectionName = collectionNameForTest

  case class TestType(testType: String)

  implicit val testTypeFormat: OFormat[TestType] = Json.format[TestType]

  class TestRepository(mongoComponent: MongoComponent)
    extends PlayMongoRepository[TestType](
      collectionName = collectionNameForTest,
      mongoComponent = mongoComponent,
      domainFormat = testTypeFormat,
      indexes = Nil
    ) with ReactiveRepositoryHelpers {

    // Use this collection when using hand written bson documents
    val testCollection: MongoCollection[Document] = mongoComponent.database.getCollection(collectionNameForTest)

    def update(updateKvp: (String, TestType)) = {
      val query = Document.empty
      val update = updateKvp match {
        case (id, data) => Document("$set" -> Document(id -> Codecs.toBson(data)))
      }
      testCollection.updateOne(query, update).toFuture()
    }

    def insert(kvp: (String, TestType)) = {
      kvp match {
        case (id, data) =>
          val insertDoc = Document(id -> Codecs.toBson(data))
          testCollection.insertOne(insertDoc).toFuture()
      }
    }

    def remove(value: String) = {
      val query = Document("id.testType" -> value)
      testCollection.deleteOne(query).toFuture()
    }
  }
}
