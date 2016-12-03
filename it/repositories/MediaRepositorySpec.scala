package repositories

import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import model.persisted.Media
import testkit.MongoRepositorySpec

class MediaRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = "media"

  def repository = new MediaMongoRepository()

  "find media" should {
    "return Some media when exists" in {
      val testUserId = "userId1"
      val testMediaStr = "Test Media"

      repository.create(Media(
        testUserId,
        testMediaStr
      )).futureValue

      val fetchMedia = repository.find(testUserId).futureValue

      fetchMedia must not be empty
      fetchMedia.get mustBe Media(testUserId, testMediaStr)
    }

    "Return no media when does not exist" in {
      val fetchMedia = repository.find("randomUserId").futureValue

      fetchMedia mustBe empty
    }
  }

  def insert(doc: BSONDocument) = repository.collection.insert(doc)
}
