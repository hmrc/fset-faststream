package repositories

import model.persisted.Media
import testkit.MongoRepositorySpec

class MediaRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.MEDIA

  def repository = new MediaMongoRepository(mongo)

  "find media" should {
    "return media when it exists" in {
      val testUserId = "userId1"
      val testMediaStr = "Test Media"
      repository.create(Media(testUserId, testMediaStr)).futureValue

      val fetchMedia = repository.find(testUserId).futureValue
      fetchMedia mustBe Some(Media(testUserId, testMediaStr))
    }

    "Return no media when does not exist" in {
      val fetchMedia = repository.find("randomUserId").futureValue
      fetchMedia mustBe empty
    }
  }

  "find all media" should {
    "return media when it exists" in {
      val testUserId = "userId1"
      val testMediaStr = "Test Media"
      repository.create(Media(testUserId, testMediaStr)).futureValue

      val fetchMedia = repository.findAll().futureValue
      fetchMedia mustBe Map(testUserId -> Media(testUserId, testMediaStr))
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

  "remove" should {
    "remove the media" in {
      val mediaItem = Media(UserId, "media")
      repository.create(mediaItem).futureValue

      val returnedMediaOpt = repository.find(UserId).futureValue
      returnedMediaOpt mustBe Some(mediaItem)

      repository.removeMedia(UserId).futureValue
      repository.find(UserId).futureValue mustBe None
    }
  }
}
