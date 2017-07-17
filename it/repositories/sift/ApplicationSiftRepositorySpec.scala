package repositories.sift

import model.ApplicationStatus
import repositories.{CollectionNames, CommonRepository}
import testkit.{MockitoSugar, MongoRepositorySpec}

/**
  * Created by andrew on 17/07/17.
  */
class ApplicationSiftRepositorySpec extends MongoRepositorySpec with CommonRepository with MockitoSugar {

  val collectionName: String = CollectionNames.APPLICATION

  "next Application for sift" should {
    "return a single matching application ready for progress to sift" in {
      insertApplication("appId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED)

      whenReady(applicationSiftRepository.nextApplicationsForSift(10)) { applications =>
        applications mustBe empty
      }
    }
  }
}
