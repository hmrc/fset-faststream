package repositories.sift

import model._
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

      val schemeDefinitions = List(Scheme(SchemeId("Commercial"), "", "", true),
        Scheme(SchemeId("Project Delivery"), "", "", true),
        Scheme(SchemeId("Generalist"), "", "", true))

      val repository = applicationSiftRepository(schemeDefinitions)

      insertApplicationWithPhase3TestNotifiedResults("appId1", PassmarkEvaluation("", Some(""),
               List(SchemeEvaluationResult(SchemeId("Commercial"),
                 EvaluationResults.Green.toPassmark)), "", Some(""))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId2", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Commercial"),
          EvaluationResults.Green.toPassmark)), "", Some(""))).futureValue
      updateApplicationStatus("appId2", ApplicationStatus.PHASE3_TESTS_PASSED)

      insertApplicationWithPhase3TestNotifiedResults("appId3", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Commercial"),
          EvaluationResults.Green.toPassmark)), "", Some(""))).futureValue
      updateApplicationStatus("appId3", ApplicationStatus.PHASE3_TESTS_FAILED)

      insertApplicationWithPhase3TestNotifiedResults("appId4", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Project Delivery"),
          EvaluationResults.Green.toPassmark)), "", Some(""))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId5", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Finance"),
          EvaluationResults.Green.toPassmark)), "", Some(""))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId6", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Generalist"),
          EvaluationResults.Red.toPassmark)), "", Some(""))).futureValue

      val appsForSift = repository.nextApplicationsForSift(10).futureValue
      appsForSift mustBe List(
        ApplicationForSift("appId1", PassmarkEvaluation("", Some(""),
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toPassmark)), "", Some(""))),
        ApplicationForSift("appId4", PassmarkEvaluation("", Some(""),
          List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Green.toPassmark)), "", Some(""))))
    }
  }
}
