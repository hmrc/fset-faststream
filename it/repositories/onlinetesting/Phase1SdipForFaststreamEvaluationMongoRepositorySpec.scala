package repositories.onlinetesting

import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Green
import model._
import model.SchemeType._
import model.persisted._
import model.persisted.phase3tests.LaunchpadTest
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatest.mock.MockitoSugar
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.CommonRepository
import testkit.MongoRepositorySpec

class Phase1SdipForFaststreamEvaluationMongoRepositorySpec extends MongoRepositorySpec with CommonRepository with MockitoSugar {

  import ImplicitBSONHandlers._
  import Phase1EvaluationMongoRepositorySpec._

  val collectionName: String = "application"
  val resultNoSdip = List(SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString))
  val resultWithSdip = List(SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString),
        SchemeEvaluationResult(SchemeType.Sdip, Green.toString)
  )

  def phase1Evaluation(result: List[SchemeEvaluationResult]) = PassmarkEvaluation("phase1_version1", None, result)
  def phase2Evaluation(result: List[SchemeEvaluationResult]) = PassmarkEvaluation("phase2_version1", None, result)

  "next Application Ready For Evaluation" should {
    "return nothing if there are no SdipFaststream applications" in {
      insertApplication("appId1", ApplicationStatus.SUBMITTED, applicationRoute = Some(ApplicationRoute.Sdip))
      insertApplication("appId2", ApplicationStatus.PHASE2_TESTS, applicationRoute = Some(ApplicationRoute.Edip))
      insertApplication("appId3", ApplicationStatus.PHASE3_TESTS, applicationRoute = Some(ApplicationRoute.Faststream))
      insertApplication("appId4", ApplicationStatus.PHASE1_TESTS, applicationRoute = Some(ApplicationRoute.Sdip))


      val result = phase1SdipForFaststreamEvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return nothing if application does not have online exercise results" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1Tests), applicationRoute = Some(ApplicationRoute.SdipFaststream))
      val result = phase1SdipForFaststreamEvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return nothing when tests have expired" in {

      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        additionalProgressStatuses = List(ProgressStatuses.PHASE1_TESTS_EXPIRED -> true),
        applicationRoute = Some(ApplicationRoute.SdipFaststream)
      )


      val result = phase1SdipForFaststreamEvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue

      result mustBe empty
    }

    "return the SdipFaststream candidate in PHASE2_TESTS if the sdip is not evaluated" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))

      insertApplication("app2", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult),
        Some(Phase2TestProfileExamples.phase2TestWithResult), phase1Evaluation = Some(phase1Evaluation(resultNoSdip)),
        applicationRoute = Some(ApplicationRoute.SdipFaststream)
      )

      insertApplication("app3", ApplicationStatus.PHASE3_TESTS, None, Some(Phase2TestProfileExamples.phase2TestWithResult),
        Some(Phase3TestProfileExamples.phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation(resultNoSdip)),
        applicationRoute = Some(ApplicationRoute.SdipFaststream)
      )

      val evaluation = PassmarkEvaluation("version1", None, resultNoSdip)
      phase1SdipForFaststreamEvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("app1", ProgressStatuses.PHASE2_TESTS_INVITED).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1SdipForFaststreamEvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result must not be empty
    }

    "do not return the SdipFaststream candidate if the sdip is already evaluated" in {


      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))

      insertApplication("app2", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult),
        Some(Phase2TestProfileExamples.phase2TestWithResult), phase1Evaluation = Some(phase1Evaluation(resultWithSdip)),
        applicationRoute = Some(ApplicationRoute.SdipFaststream)
      )

      insertApplication("app3", ApplicationStatus.PHASE3_TESTS, None, Some(Phase2TestProfileExamples.phase2TestWithResult),
        Some(Phase3TestProfileExamples.phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation(resultWithSdip)),
        applicationRoute = Some(ApplicationRoute.SdipFaststream)
      )

      val evaluation = PassmarkEvaluation("version1", None, resultWithSdip)

      phase1SdipForFaststreamEvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("app1", ProgressStatuses.PHASE2_TESTS_INVITED).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("app2", ProgressStatuses.PHASE3_TESTS_INVITED).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("app3", ProgressStatuses.PHASE3_TESTS_SUCCESS_NOTIFIED).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1SdipForFaststreamEvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }
  }

  private def getOnePhase1Profile(appId: String) = {
    phase1SdipForFaststreamEvaluationRepo.collection.find(BSONDocument("applicationId" -> appId)).one[BSONDocument].map(_.map { doc =>
      val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
      val bsonPhase1 = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument]("PHASE1"))
      val phase1 = bsonPhase1.map(Phase1TestProfile.bsonHandler.read).get
      (applicationStatus, phase1)
    }).futureValue
  }
}

object Phase1SdipForFaststreamEvaluationMongoRepositorySpec {
  val now = DateTime.now().withZone(DateTimeZone.UTC)
  val phase1Tests = List(
    CubiksTest(16196, usedForResults = true, 100, "cubiks", "token1", "http://localhost", now, 2000),
    CubiksTest(16194, usedForResults = true, 101, "cubiks", "token2", "http://localhost", now, 2001)
  )
  val phase1TestsWithResult = phase1TestsWithResults(TestResult("Ready", "norm", Some(20.5), None, None, None))
  def phase1TestsWithResults(testResult: TestResult) = {
    phase1Tests.map(t => t.copy(testResult = Some(testResult)))
  }
}
