package repositories

import model.Exceptions.NotFoundException
import play.api.libs.json.Json
import reactivemongo.bson._
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

class ReactiveRepositoryHelpersSpec extends MongoRepositorySpec {
  val collectionName = "reactive-repository-test-spec"

  val testValue = "id" -> TestType("testId")
  case class TestType(testValue: String)
  implicit val testJSONFormat = Json.format[TestType]
  implicit val testBSONFormat = Macros.handler[TestType]

  "Update Validator" should {
    "return Unit when successfully updating a document" in new TestFixture {
      repo.insert(testValue).futureValue
      val result = repo.update(testValue) map repo.singleUpdateValidator("id", "testing")
      result.futureValue mustBe unit
    }
    "throw when update does not affect any documents" in new TestFixture {
      val result = repo.update(testValue) map repo.singleUpdateValidator("id", "testing")
      result.failed.futureValue mustBe a[NotFoundException]
    }
    "return Unit update does not affect any documents, and ignoreNotFound enabled" in new TestFixture {
      val result = repo.update(testValue) map repo.singleUpdateValidator("id", "testing", ignoreNotFound = true)
      result.futureValue mustBe unit
    }
    "throw custom exception when update does not affect any documents, and custom exception provided" in new TestFixture {
      class TestException extends Exception
      val result = repo.update(testValue) map repo.singleUpdateValidator("id", "testing", new TestException)
      result.failed.futureValue mustBe a[TestException]
    }
  }
  "Upsert Validator" should {
    "return Unit when successfully inserting a document" in new TestFixture {
      val result = repo.update(testValue) map repo.singleUpsertValidator("id", "testing")
      result.futureValue mustBe unit
    }
    "return Unit when successfully upserting a document" in new TestFixture {
      repo.insert(testValue).futureValue
      val result = repo.update(testValue) map repo.singleUpsertValidator("id", "testing")
      result.futureValue mustBe unit
    }
  }
  "Delete Validator" should {
    "return Unit when successfully removing a document" in new TestFixture {
      repo.insert(testValue).futureValue
      val result = repo.remove("id.testValue" -> "testId") map repo.singleRemovalValidator("id", "testing")
      result.futureValue mustBe unit
    }
    "throw NotFoundException when removing a non-existent document" in new TestFixture {
      repo.insert(testValue).futureValue
      val result = repo.remove("id.testValue" -> "wrong-id") map repo.singleRemovalValidator("id", "testing")
      result.failed.futureValue mustBe a[NotFoundException]
    }
  }

  trait TestFixture {
    val repo = new TestRepository()
  }

  class TestRepository(implicit mongo: () => DB)
    extends ReactiveRepository[TestType, BSONObjectID](collectionName, mongo,
      testJSONFormat, ReactiveMongoFormats.objectIdFormats) with ReactiveRepositoryHelpers {

    def update(updateKvp: (String, TestType), queryKvp: Option[(String, TestType)] = None) = {
      val query = queryKvp.map(BSONDocument(_)).getOrElse(BSONDocument.empty)
      collection.update(ordered = false).one(query, BSONDocument(updateKvp))
    }

    def insert(kvp: (String, TestType)) = collection.insert(ordered = false).one(BSONDocument(kvp))
  }
}
