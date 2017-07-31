package repositories.assessmentcentre

import model.ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION
import model._
import model.command.ApplicationForFsac
import model.persisted.fsac.{ AnalysisExercise, AssessmentCentreTests }
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import org.scalatest.concurrent.ScalaFutures
import play.api.Logger
import repositories.application.GeneralApplicationRepository
import repositories.sift.ApplicationSiftRepository
import repositories.{ CollectionNames, CommonRepository }
import testkit.MongoRepositorySpec

import scala.concurrent.Future

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

  "next Application for sift" must {
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
        appsForAc must contain(
          ApplicationForFsac("appId1", PassmarkEvaluation("", Some(""),
            List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)), "", Some("")), Nil)
        )
        appsForAc must contain(
          ApplicationForFsac("appId4", PassmarkEvaluation("", Some(""),
            List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Green.toString)), "", Some("")), Nil)
        )
        appsForAc.length mustBe 2
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

  "progressToFsac" must {
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

  "getTests" must {
    "get tests when they exist" in new TestFixture {
      insertApplicationWithAssessmentCentreAwaitingAllocation("appId1")
      repository.getTests("appId1").futureValue mustBe expectedAssessmentCentreTests
    }

    "return empty when there are no tests" in new TestFixture {
      insertApplicationWithAssessmentCentreAwaitingAllocation("appId1", withTests = false)
      repository.getTests("appId1").futureValue mustBe AssessmentCentreTests()
    }
  }

  "updateTests" must {
    "update the tests key and be retrievable" in new TestFixture {
      insertApplication("appId1", ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_AWAITING_ALLOCATION -> true)
      )

      repository.updateTests("appId1", expectedAssessmentCentreTests).futureValue

      repository.getTests("appId1").futureValue mustBe expectedAssessmentCentreTests
    }
  }

  trait TestFixture {

    val expectedAssessmentCentreTests = AssessmentCentreTests(
      Some(AnalysisExercise(
        fileId = "fileId1"
      ))
    )

    def insertApplicationWithAssessmentCentreAwaitingAllocation(appId: String, withTests: Boolean = true): Unit = {
      insertApplication(appId, ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = List(ASSESSMENT_CENTRE_AWAITING_ALLOCATION -> true)
      )

      if (withTests) {
        repository.updateTests(appId, expectedAssessmentCentreTests).futureValue
      }
    }
  }
}
