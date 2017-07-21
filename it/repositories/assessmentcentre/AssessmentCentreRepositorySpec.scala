package repositories.assessmentcentre

import model.EvaluationResults.Green
import model.Phase3TestProfileExamples.phase3TestWithResult
import model.ProgressStatuses.PHASE3_TESTS_PASSED
import model._
import model.command.ApplicationForSift
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import org.scalatest.concurrent.ScalaFutures
import repositories.onlinetesting.Phase2EvaluationMongoRepositorySpec.phase2TestWithResult
import repositories.{ CollectionNames, CommonRepository }
import testkit.{ MockitoSugar, MongoRepositorySpec }

class AssessmentCentreRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository {

  val collectionName: String = CollectionNames.APPLICATION

  val Commercial: SchemeId = SchemeId("Commercial")
  val European: SchemeId = SchemeId("European")
  val Sdip: SchemeId = SchemeId("Sdip")
  val Generalist: SchemeId = SchemeId("Generalistt status")
  val ProjectDelivery = SchemeId("Project Delivery")
  val Finance = SchemeId("Finance")
  val DiplomaticService = SchemeId("Diplomatic Service")
  val siftableSchemeDefinitions = List(European, Finance, DiplomaticService)

  def repository: AssessmentCentreMongoRepository = assessmentCentreRepository(siftableSchemeDefinitions)

  "next Application for sift" should {
    "ignore applications in incorrect statuses and return only the Phase3 Passed_Notified applications that are not eligible for sift" in {
      insertApplicationWithPhase3TestNotifiedResults("appId1", SchemeId("Commercial"), EvaluationResults.Green).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId2", SchemeId("Commercial"), EvaluationResults.Green).futureValue
      updateApplicationStatus("appId2", ApplicationStatus.PHASE3_TESTS_PASSED)

      insertApplicationWithPhase3TestNotifiedResults("appId3", SchemeId("Commercial"), EvaluationResults.Green).futureValue
      updateApplicationStatus("appId3", ApplicationStatus.PHASE3_TESTS_FAILED)

      insertApplicationWithPhase3TestNotifiedResults("appId4", SchemeId("Project Delivery"), EvaluationResults.Green).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId5", SchemeId("Finance"), EvaluationResults.Green).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId6", SchemeId("Generalist"), EvaluationResults.Red).futureValue

      whenReady(repository.nextApplicationForAssessmentCentre(10)) { appsForAc =>
        appsForAc mustBe List(
          ApplicationForSift("appId1", PassmarkEvaluation("", Some(""),
            List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toPassmark)), "", Some(""))),
          ApplicationForSift("appId4", PassmarkEvaluation("", Some(""),
            List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Green.toPassmark)), "", Some(""))))
      }
    }

    ("return no results when there are only phase 3 applications that aren't in Passed_Notified which don't apply for sift or don't have "
       + "Green/Passed results") in {
      insertApplicationWithPhase3TestNotifiedResults("appId7", SchemeId("Finance"), EvaluationResults.Green).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId8", SchemeId("Generalist"), EvaluationResults.Green).futureValue
      updateApplicationStatus("appId8", ApplicationStatus.PHASE3_TESTS_FAILED)
      insertApplicationWithPhase3TestNotifiedResults("appId9", SchemeId("Finance"), EvaluationResults.Green).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId10", SchemeId("Project Delivery"), EvaluationResults.Red).futureValue

      whenReady(repository.nextApplicationForAssessmentCentre(10)) { appsForAc =>
        appsForAc mustBe Nil
      }
    }
  }
}
