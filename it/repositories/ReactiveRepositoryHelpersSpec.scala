package repositories

import model.Exceptions.{CannotUpdateRecord, NotFoundException}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.Json
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
      // TODO: mongo - check to see if it is ok to change the exception type (might be a problem)
      result.failed.futureValue mustBe a[CannotUpdateRecord]
    }
    "return Unit when update does not affect any documents, and ignoreNotFound enabled" in new TestFixture {
      val result = repo.update(testType1) map repo.singleUpdateValidator("id", "testing", ignoreNoRecordUpdated = true)
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

  val collectionName = "reactive-repository-helpers-test"

  case class TestType(testType: String)
  implicit val testTypeFormat = Json.format[TestType]

  class TestRepository(mongoComponent: MongoComponent)
    extends PlayMongoRepository[TestType](
      collectionName = collectionName,
      mongoComponent = mongoComponent,
      domainFormat = testTypeFormat,
      indexes = Nil
    ) with ReactiveRepositoryHelpers {

    // Use this collection when using hand written bson documents
    val testCollection: MongoCollection[Document] = mongoComponent.database.getCollection(collectionName)

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
