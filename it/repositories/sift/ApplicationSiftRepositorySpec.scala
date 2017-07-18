package repositories.sift

import model._
import model.command.ApplicationForSift
import model.persisted.{PassmarkEvaluation, SchemeEvaluationResult}
import org.scalatest.concurrent.ScalaFutures
import repositories.{CollectionNames, CommonRepository}
import testkit.{MockitoSugar, MongoRepositorySpec}

class ApplicationSiftRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository with MockitoSugar {

  val collectionName: String = CollectionNames.APPLICATION

  val schemeDefinitions = List(Scheme(SchemeId("Commercial"), "", "", true),
    Scheme(SchemeId("Project Delivery"), "", "", true),
    Scheme(SchemeId("Generalist"), "", "", true))

  "next Application for sift" should {
    "ignore applications in incorrect statuses and return only the Phase3 Passed_Notified applications that are eligible for sift" in {

      val repository = applicationSiftRepository(schemeDefinitions)

      insertApplicationWithPhase3TestNotifiedResults("appId1", SchemeId("Commercial"), EvaluationResults.Green).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId2", SchemeId("Commercial"), EvaluationResults.Green).futureValue
      updateApplicationStatus("appId2", ApplicationStatus.PHASE3_TESTS_PASSED)

      insertApplicationWithPhase3TestNotifiedResults("appId3", SchemeId("Commercial"), EvaluationResults.Green).futureValue
      updateApplicationStatus("appId3", ApplicationStatus.PHASE3_TESTS_FAILED)

      insertApplicationWithPhase3TestNotifiedResults("appId4", SchemeId("Project Delivery"), EvaluationResults.Green).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId5", SchemeId("Finance"), EvaluationResults.Green).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId6", SchemeId("Generalist"), EvaluationResults.Red).futureValue

      val appsForSift = repository.nextApplicationsForSift(10).futureValue
      appsForSift mustBe List(
        ApplicationForSift("appId1", PassmarkEvaluation("", Some(""),
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toPassmark)), "", Some(""))),
        ApplicationForSift("appId4", PassmarkEvaluation("", Some(""),
          List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Green.toPassmark)), "", Some(""))))
    }

    """return no results when there are only phase 3 applications that aren't in Passed_Notified which apply for sift or don't have Green/Passed
       |results""".stripMargin.replaceAll("\n", " ") in {

      val repository = applicationSiftRepository(schemeDefinitions)

      insertApplicationWithPhase3TestNotifiedResults("appId7", SchemeId("Finance"), EvaluationResults.Green).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId8", SchemeId("Generalist"), EvaluationResults.Green).futureValue
      updateApplicationStatus("appId8", ApplicationStatus.PHASE3_TESTS_FAILED)
      insertApplicationWithPhase3TestNotifiedResults("appId9", SchemeId("Finance"), EvaluationResults.Green).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId10", SchemeId("Project Delivery"), EvaluationResults.Red).futureValue

      val appsForSift = repository.nextApplicationsForSift(10).futureValue
      appsForSift mustBe Nil
    }
  }
}
