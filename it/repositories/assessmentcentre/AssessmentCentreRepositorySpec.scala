package repositories.assessmentcentre

import model._
import model.command.ApplicationForFsac
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import org.scalatest.concurrent.ScalaFutures
import repositories.application.GeneralApplicationRepository
import repositories.sift.ApplicationSiftRepository
import repositories.{ CollectionNames, CommonRepository }
import testkit.MongoRepositorySpec

class AssessmentCentreRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository {

  val collectionName: String = CollectionNames.APPLICATION

  val Commercial: SchemeId = SchemeId("Commercial")
  val European: SchemeId = SchemeId("European")
  val Sdip: SchemeId = SchemeId("Sdip")
  val Generalist: SchemeId = SchemeId("Generalist")
  val ProjectDelivery = SchemeId("Project Delivery")
  val Finance = SchemeId("Finance")
  val DiplomaticService = SchemeId("Diplomatic Service")
  val siftableSchemeDefinitions = List(European, Finance, DiplomaticService)

  def repository = assessmentCentreRepository(siftableSchemeDefinitions)
  def siftRepository = applicationSiftRepository(siftableSchemeDefinitions)

  "next Application for sift" should {
    "ignore applications in incorrect statuses and return only the Phase3 Passed_Notified applications that are not eligible for sift" in {
      insertApplicationWithPhase3TestNotifiedResults("appId1",
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId2",
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId2", ApplicationStatus.PHASE3_TESTS_PASSED)

      insertApplicationWithPhase3TestNotifiedResults("appId3",
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId3", ApplicationStatus.PHASE3_TESTS_FAILED)

      insertApplicationWithPhase3TestNotifiedResults("appId4",
        List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId5",
        List(SchemeEvaluationResult(SchemeId("Finance"), EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId6",
        List(SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Red.toString))).futureValue

      whenReady(repository.nextApplicationForAssessmentCentre(10)) { appsForAc =>
        appsForAc mustBe List(
          ApplicationForFsac("appId1", PassmarkEvaluation("", Some(""),
            List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)), "", Some("")), Nil),
          ApplicationForFsac("appId4", PassmarkEvaluation("", Some(""),
            List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Green.toString)), "", Some("")), Nil))
      }
    }

    ("return no results when there are only phase 3 applications that aren't in Passed_Notified which don't apply for sift or don't have "
       + "Green/Passed results") in {
      insertApplicationWithPhase3TestNotifiedResults("appId7",
        List(SchemeEvaluationResult(SchemeId("Finance"), EvaluationResults.Green.toString))).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId8",
        List(SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId8", ApplicationStatus.PHASE3_TESTS_FAILED)
      insertApplicationWithPhase3TestNotifiedResults("appId9",
        List(SchemeEvaluationResult(SchemeId("Finance"), EvaluationResults.Green.toString))).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId10",
        List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Red.toString))).futureValue

      whenReady(repository.nextApplicationForAssessmentCentre(10)) { appsForAc =>
        appsForAc mustBe Nil
      }
    }
  }

  "progressToFsac" should {
    "update cumulative evaluation results from sift and phase 3" in {
      insertApplicationWithPhase3TestNotifiedResults("appId11",
        List(SchemeEvaluationResult(SchemeId("Finance"), EvaluationResults.Green.toString),
          SchemeEvaluationResult(Generalist, EvaluationResults.Red.toString))).futureValue

      applicationRepository.addProgressStatusAndUpdateAppStatus("appId11", ProgressStatuses.ALL_SCHEMES_SIFT_ENTERED).futureValue
      siftRepository.siftApplicationForScheme("appId11", SchemeEvaluationResult(Finance, EvaluationResults.Green.toString)).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("appId11", ProgressStatuses.ALL_SCHEMES_SIFT_COMPLETED).futureValue

      val nextResults = repository.nextApplicationForAssessmentCentre(1).futureValue
      nextResults mustBe List(
        ApplicationForFsac("appId11",
          PassmarkEvaluation("", Some(""), List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString),
            SchemeEvaluationResult(Generalist, EvaluationResults.Red.toString)), "", Some("")),
          List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString)))
      )

      repository.progressToAssessmentCentre(nextResults.head, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION).futureValue
    }
  }
}
