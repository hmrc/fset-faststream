package repositories

import model.persisted.Media
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import testkit.MongoRepositorySpec

class MediaRepositorySpec extends MongoRepositorySpec {

  import ImplicitBSONHandlers._

  override val collectionName: String = CollectionNames.MEDIA

  def repository = new MediaMongoRepository(mongo)

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

  "Clone media" should {
    "archive the existing media item and create a new one" in {
      val mediaItem = Media(UserId, "media")
      repository.create(mediaItem).futureValue

      val userIdToArchiveWith = "newUserId"
      repository.cloneAndArchive(UserId, userIdToArchiveWith).futureValue

      val newMediaItem = repository.find(UserId).futureValue.get
      newMediaItem.userId mustBe UserId
      newMediaItem.originalUserId mustBe None

      val archivedMediaItem = repository.find(userIdToArchiveWith).futureValue.get
      archivedMediaItem.userId mustBe userIdToArchiveWith
      archivedMediaItem.originalUserId mustBe Some(UserId)
    }
  }

  private def insert(doc: BSONDocument) = repository.collection.insert(ordered = false).one(doc)
}
