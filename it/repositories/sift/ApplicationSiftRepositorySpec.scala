package repositories.sift

import model.{ApplicationStatus, ProgressStatuses, SchemeId}
import model.command.ApplicationForSift
import model.persisted.phase3tests.{LaunchpadTest, Phase3TestGroup}
import model.persisted.{PassmarkEvaluation, SchemeEvaluationResult}
import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures
import reactivemongo.bson.BSONDocument
import repositories.{CollectionNames, CommonRepository}
import testkit.{MockitoSugar, MongoRepositorySpec}

/**
  * Created by andrew on 17/07/17.
  */
class ApplicationSiftRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository with MockitoSugar {

  val collectionName: String = CollectionNames.APPLICATION

  "next Application for sift" should {
    "return a single matching application ready for progress to sift" in {
//      insertApplication("appId1", ApplicationStatus.PHASE3_TESTS)
//      insertApplication("appId1", Some(100), PassmarkEvaluation("", Some(""),
//        List(SchemeEvaluationResult(SchemeId("Commercial"), "Green")), "", Some("")))(SchemeId("Commercial"))

      insertApplicationWithPhase3TestNotifiedResults("appId1", Some(100), PassmarkEvaluation("", Some(""),
               List(SchemeEvaluationResult(SchemeId("Commercial"), "Green")), "", Some("")))(SchemeId("Commercial"))

//      phase3TestRepository.insertOrUpdateTestGroup("appId1", Phase3TestGroup(DateTime.now(), List(), Some(PassmarkEvaluation("", Some(""),
//        List(SchemeEvaluationResult(SchemeId("Commercial"), "Green")), "", Some(""))))).futureValue

//      val f1 = applicationSiftRepository.nextApplicationsForSift2(1)
//      f1.futureValue mustBe BSONDocument

      val f = applicationSiftRepository.nextApplicationsForSift(1)
      f.futureValue mustBe
        List(ApplicationForSift("", PassmarkEvaluation("", None, Nil, "", None)))
    }
  }
}
