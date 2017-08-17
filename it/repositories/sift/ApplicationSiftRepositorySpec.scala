package repositories.sift

import model.EvaluationResults.{ Green, Red, Withdrawn }
import model.Phase3TestProfileExamples.phase3TestWithResult
import model.ProgressStatuses.{ PHASE3_TESTS_PASSED, PHASE3_TESTS_PASSED_NOTIFIED }
import model._
import model.command.ApplicationForSift
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import org.scalatest.CancelAfterFailure
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.TableDrivenPropertyChecks
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase2EvaluationMongoRepositorySpec.phase2TestWithResult
import repositories.{ CollectionNames, CommonRepository }
import testkit.{ MockitoSugar, MongoRepositorySpec }

class ApplicationSiftRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository
  with MockitoSugar with TableDrivenPropertyChecks {

  val collectionName: String = CollectionNames.APPLICATION

  val Commercial: SchemeId = SchemeId("Commercial")
  val Sdip: SchemeId = SchemeId("Sdip")
  val Generalist: SchemeId = SchemeId("Generalist")
  val ProjectDelivery = SchemeId("Project Delivery")
  val schemeDefinitions = List(Commercial, ProjectDelivery, Generalist)

  def repository: ApplicationSiftMongoRepository = applicationSiftRepository

  "next Application for sift" should {
    "ignore applications in incorrect statuses and return only the Phase3 Passed_Notified applications that are eligible for sift" in {
      insertApplicationWithPhase3TestNotifiedResults("appId1",
        List(SchemeEvaluationResult(DiplomaticService, EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId2",
        List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId2", ApplicationStatus.PHASE3_TESTS_FAILED)

      insertApplicationWithPhase3TestNotifiedResults("appId3",
        List(SchemeEvaluationResult(European, EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId4",
        List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString))).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId5",
        List(SchemeEvaluationResult(Generalist, EvaluationResults.Red.toString))).futureValue

      val appsForSift = repository.nextApplicationsForSiftStage(10).futureValue
      appsForSift must contain theSameElementsAs List(
        ApplicationForSift("appId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(DiplomaticService, EvaluationResults.Green.toString))),
        ApplicationForSift("appId3", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(European, EvaluationResults.Green.toString))),
        ApplicationForSift("appId4", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString)))
      )

      appsForSift.size mustBe 3
    }

    ("return no results when there are only phase 3 applications that aren't in Passed_Notified which apply for sift or don't have Green/Passed "
       + "results") in {
      insertApplicationWithPhase3TestResults("appId7", None,
        PassmarkEvaluation("1", None, List(SchemeEvaluationResult(Finance, EvaluationResults.Green.toString)), "1", None))(Finance)

      insertApplicationWithPhase3TestNotifiedResults("appId8",
        List(SchemeEvaluationResult(Generalist, EvaluationResults.Green.toString))).futureValue
      updateApplicationStatus("appId8", ApplicationStatus.PHASE3_TESTS_FAILED)
      insertApplicationWithPhase3TestNotifiedResults("appId9",
        List(SchemeEvaluationResult(Finance, EvaluationResults.Red.toString))).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId10",
        List(SchemeEvaluationResult(ProjectDelivery, EvaluationResults.Red.toString))).futureValue

      val appsForSift = repository.nextApplicationsForSiftStage(10).futureValue
      appsForSift mustBe Nil
    }
  }

  "findApplicationsReadyForSifting" should {

    "return candidates that are ready for sifting" in {
      createSiftEligibleCandidates(UserId, "appId1", resultToSave = List(SchemeEvaluationResult(Commercial, Green.toString)))
      createSiftEligibleCandidates(UserId, "appId2", resultToSave = List(SchemeEvaluationResult(Commercial, Withdrawn.toString)))
      createSiftEligibleCandidates(UserId, "appId3", resultToSave = List(SchemeEvaluationResult(Commercial, Red.toString)))
      createSiftEligibleCandidates(UserId, "appId4", resultToSave = List(SchemeEvaluationResult(Generalist, Green.toString)))

      val candidates = repository.findApplicationsReadyForSchemeSift(Commercial).futureValue
      candidates.size mustBe 1
      val candidate = candidates.head
      candidate.applicationId mustBe Some("appId1")
    }

  }

  "siftCandidate" should {
    "sift candidate as Passed" in {
      createSiftEligibleCandidates(UserId, "appId12")
      repository.siftApplicationForScheme("appId12", SchemeEvaluationResult(Commercial, "Green")).futureValue
      val candidates = repository.findApplicationsReadyForSchemeSift(Commercial).futureValue
      candidates.size mustBe 0
    }

    "submit difference schemes" in {
      createSiftEligibleCandidates(UserId, "appId13")
      repository.siftApplicationForScheme("appId13", SchemeEvaluationResult(European, "Red")).futureValue
      repository.siftApplicationForScheme("appId13", SchemeEvaluationResult(Commercial, "Green")).futureValue
    }

    "eligible for other schema after sifting on one" in {
      createSiftEligibleCandidates(UserId, "appId14")
      repository.siftApplicationForScheme("appId14", SchemeEvaluationResult(European, "Red")).futureValue
      val candidates = repository.findApplicationsReadyForSchemeSift(Sdip).futureValue
      candidates.size mustBe 1
    }

    "not sift applictaion for already sifted scheme" in {
      createSiftEligibleCandidates(UserId, "appId15")
      repository.siftApplicationForScheme("appId15", SchemeEvaluationResult(Commercial, "Green")).futureValue
      intercept[Exception] {
        repository.siftApplicationForScheme("appId15", SchemeEvaluationResult(Commercial, "Red")).futureValue
      }
    }
  }

  private def createSiftEligibleCandidates(userId: String, appId: String, resultToSave: List[SchemeEvaluationResult] = List(
      SchemeEvaluationResult(Commercial, Green.toString),
      SchemeEvaluationResult(Sdip, Green.toString),
      SchemeEvaluationResult(European, Green.toString),
      SchemeEvaluationResult(Generalist, Red.toString)
    )
  ) = {

    val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version2-res", None)
    insertApplication(appId,
      ApplicationStatus.PHASE3_TESTS, None, Some(phase2TestWithResult),
      Some(phase3TestWithResult),
      schemes = List(Commercial, European),
      phase2Evaluation = Some(phase2Evaluation))

    val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave,
      "phase3_version1-res", Some("phase2_version1-res"))
    phase3EvaluationRepo.savePassmarkEvaluation(appId, phase3Evaluation, Some(PHASE3_TESTS_PASSED)).futureValue
    applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_READY).futureValue
  }
}
