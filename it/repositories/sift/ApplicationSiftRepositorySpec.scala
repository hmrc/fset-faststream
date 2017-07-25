package repositories.sift

import model.EvaluationResults.{ Green, Red }
import model.Phase3TestProfileExamples.phase3TestWithResult
import model.ProgressStatuses.PHASE3_TESTS_PASSED
import model._
import model.command.ApplicationForSift
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import org.scalatest.CancelAfterFailure
import org.scalatest.concurrent.ScalaFutures
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase2EvaluationMongoRepositorySpec.phase2TestWithResult
import repositories.{ CollectionNames, CommonRepository }
import testkit.{ MockitoSugar, MongoRepositorySpec }

class ApplicationSiftRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository
  with MockitoSugar {

  val collectionName: String = CollectionNames.APPLICATION

  val Commercial: SchemeId = SchemeId("Commercial")
  val European: SchemeId = SchemeId("European")
  val Sdip: SchemeId = SchemeId("Sdip")
  val Generalist: SchemeId = SchemeId("Generalist")
  val ProjectDelivery = SchemeId("Project Delivery")
  val schemeDefinitions = List(Commercial, ProjectDelivery, Generalist)

  def repository: ApplicationSiftMongoRepository = applicationSiftRepository(schemeDefinitions)

  "next Application for sift" should {
    "ignore applications in incorrect statuses and return only the Phase3 Passed_Notified applications that are eligible for sift" in {
      insertApplicationWithPhase3TestNotifiedResults("appId1", SchemeId("Commercial"), EvaluationResults.Green).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId2", SchemeId("Commercial"), EvaluationResults.Green).futureValue
      updateApplicationStatus("appId2", ApplicationStatus.PHASE3_TESTS_PASSED)

      insertApplicationWithPhase3TestNotifiedResults("appId3", SchemeId("Commercial"), EvaluationResults.Green).futureValue
      updateApplicationStatus("appId3", ApplicationStatus.PHASE3_TESTS_FAILED)

      insertApplicationWithPhase3TestNotifiedResults("appId4", SchemeId("Project Delivery"), EvaluationResults.Green).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId5", SchemeId("Finance"), EvaluationResults.Green).futureValue

      insertApplicationWithPhase3TestNotifiedResults("appId6", SchemeId("Generalist"), EvaluationResults.Red).futureValue

      val appsForSift = repository.nextApplicationsForSiftStage(10).futureValue
      appsForSift mustBe List(
        ApplicationForSift("appId1", PassmarkEvaluation("", Some(""),
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toPassmark)), "", Some(""))),
        ApplicationForSift("appId4", PassmarkEvaluation("", Some(""),
          List(SchemeEvaluationResult(SchemeId("Project Delivery"), EvaluationResults.Green.toPassmark)), "", Some(""))))
    }

    ("return no results when there are only phase 3 applications that aren't in Passed_Notified which apply for sift or don't have Green/Passed "
       + "results") in {
      insertApplicationWithPhase3TestNotifiedResults("appId7", SchemeId("Finance"), EvaluationResults.Green).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId8", SchemeId("Generalist"), EvaluationResults.Green).futureValue
      updateApplicationStatus("appId8", ApplicationStatus.PHASE3_TESTS_FAILED)
      insertApplicationWithPhase3TestNotifiedResults("appId9", SchemeId("Finance"), EvaluationResults.Green).futureValue
      insertApplicationWithPhase3TestNotifiedResults("appId10", SchemeId("Project Delivery"), EvaluationResults.Red).futureValue

      val appsForSift = repository.nextApplicationsForSiftStage(10).futureValue
      appsForSift mustBe Nil
    }
  }

  "findApplicationsReadyForSifting" should {
    "return candidates that are ready for sifting" in {
      createSiftEligibleCandidates(UserId, "appId11")

      val candidates = repository.findApplicationsReadyForSchemeSift(Commercial).futureValue
      candidates.size mustBe 1
      val candidate = candidates.head
      candidate.applicationId mustBe Some("appId11")
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

  private def createSiftEligibleCandidates(userId: String, appId: String) = {
    val resultToSave = List(
      SchemeEvaluationResult(Commercial, Green.toString),
      SchemeEvaluationResult(Sdip, Green.toString),
      SchemeEvaluationResult(European, Green.toString),
      SchemeEvaluationResult(Generalist, Red.toString)
    )

    val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version2-res", None)
    insertApplication(appId,
      ApplicationStatus.PHASE3_TESTS, None, Some(phase2TestWithResult),
      Some(phase3TestWithResult),
      schemes = List(Commercial, Sdip, European),
      phase2Evaluation = Some(phase2Evaluation))

    val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave,
      "phase3_version1-res", Some("phase2_version1-res"))
    phase3EvaluationRepo.savePassmarkEvaluation(appId, phase3Evaluation, Some(PHASE3_TESTS_PASSED)).futureValue
    applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ALL_SCHEMES_SIFT_ENTERED).futureValue
  }
}
