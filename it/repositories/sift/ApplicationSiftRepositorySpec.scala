package repositories.sift

import model.{ApplicationStatus, SchemeId}
import model.command.ApplicationForSift
import model.persisted.{PassmarkEvaluation, SchemeEvaluationResult}
import org.scalatest.concurrent.ScalaFutures
import repositories.{CollectionNames, CommonRepository}
import testkit.{MockitoSugar, MongoRepositorySpec}

/**
  * Created by andrew on 17/07/17.
  */
class ApplicationSiftRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository with MockitoSugar {

  val collectionName: String = CollectionNames.APPLICATION

  "next Application for sift" should {
    "return a single matching application ready for progress to sift" in {
      insertApplicationWithPhase3TestNotifiedResults("appId1", Some(100), PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Commercial"), "passed")), "", Some("")))(SchemeId("Commercial"))

      whenReady(applicationSiftRepository.nextApplicationsForSift(10)) { applications =>
        applications mustBe List(ApplicationForSift("", PassmarkEvaluation("", None, Nil, "", None)))
      }
    }
  }
}
