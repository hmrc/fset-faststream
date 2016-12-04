package repositories

import model.persisted.Media
import testkit.MongoRepositorySpec


class MediaRepositorySpec extends MongoRepositorySpec {
  override val collectionName = "media"

  def repo = new MediaMongoRepository()

  "Clone media" should {
    "archive the existing media item and create a new one" in {
      val mediaItem = Media(UserId, "media")
      repo.create(mediaItem).futureValue

      val userIdToArchiveWith = "newUserId"
      repo.cloneAndArchive(UserId, userIdToArchiveWith).futureValue

      val newMediaItem = repo.find(UserId).futureValue.get
      newMediaItem.userId mustBe UserId
      newMediaItem.originalUserId mustBe None

      val archivedMediaItem = repo.find(userIdToArchiveWith).futureValue.get
      archivedMediaItem.userId mustBe userIdToArchiveWith
      archivedMediaItem.originalUserId mustBe Some(UserId)
    }
  }

}
