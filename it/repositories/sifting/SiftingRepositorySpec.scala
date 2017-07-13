package repositories.sifting

import model.EvaluationResults.Green
import model.Phase3TestProfileExamples.phase3TestWithResult
import model.ProgressStatuses.PHASE3_TESTS_PASSED
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationStatus, SchemeId }
import repositories.onlinetesting.Phase2EvaluationMongoRepositorySpec.phase2TestWithResult
import repositories.{ CollectionNames, CommonRepository }
import testkit.MongoRepositorySpec

class SiftingRepositorySpec extends MongoRepositorySpec with CommonRepository {

  override val collectionName = CollectionNames.APPLICATION

  def repository = new SiftingMongoRepository()

  private val userId = "123"

  private val Commercial: SchemeId = SchemeId("Commercial")
  private val European: SchemeId = SchemeId("European")
  private val Sdip: SchemeId = SchemeId("Sdip")

  "findApplicationsReadyForSifting" should {
    "return candidates that are ready for sifting" in {
      createSiftEligibleCandidates(UserId, AppId)

      val candidates = repository.findApplicationsReadyForSifting(Commercial).futureValue
      candidates.size mustBe 1
      val candidate = candidates.head
      candidate.applicationId mustBe Some(AppId)
    }

  }

  "siftCandidate" should {
    "sift candidate as Passed" in {
      createSiftEligibleCandidates(UserId, AppId)
      repository.siftCandidateApplication(AppId, SchemeEvaluationResult(Commercial, "Green")).futureValue
      val candidates = repository.findApplicationsReadyForSifting(Commercial).futureValue
      candidates.size mustBe 0
    }

    "submit difference schemes" in {
      createSiftEligibleCandidates(UserId, AppId)
      repository.siftCandidateApplication(AppId, SchemeEvaluationResult(European, "Red")).futureValue
      repository.siftCandidateApplication(AppId, SchemeEvaluationResult(Commercial, "Green")).futureValue
    }

    "eligible for other schema after sifting on one" in {
      createSiftEligibleCandidates(UserId, AppId)
      repository.siftCandidateApplication(AppId, SchemeEvaluationResult(European, "Red")).futureValue
      val candidates = repository.findApplicationsReadyForSifting(Sdip).futureValue
      candidates.size mustBe 1
    }

    "not sift applictaion for already sifted scheme" in {
      createSiftEligibleCandidates(UserId, AppId)
      repository.siftCandidateApplication(AppId, SchemeEvaluationResult(Commercial, "Green")).futureValue
      intercept[Exception] {
        repository.siftCandidateApplication(AppId, SchemeEvaluationResult(Commercial, "Red")).futureValue
      }
    }

  }

  private def createSiftEligibleCandidates(userId: String, appId: String) = {
    val resultToSave = List(
      SchemeEvaluationResult(Commercial, Green.toString),
      SchemeEvaluationResult(Sdip, Green.toString),
      SchemeEvaluationResult(European, Green.toString)
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

  }

}
