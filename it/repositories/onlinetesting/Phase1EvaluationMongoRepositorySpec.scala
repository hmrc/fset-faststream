package repositories.onlinetesting

import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Green
import model.SchemeType._
import model.persisted.{ ApplicationReadyForEvaluation, CubiksTest, Phase1TestProfile, _ }
import model.{ ApplicationRoute, ApplicationStatus, ProgressStatuses, SchemeType }
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatest.mock.MockitoSugar
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.CommonRepository
import testkit.MongoRepositorySpec

class Phase1EvaluationMongoRepositorySpec extends MongoRepositorySpec with CommonRepository with MockitoSugar {

  import ImplicitBSONHandlers._
  import Phase1EvaluationMongoRepositorySpec._

  val collectionName: String = "application"

  "next Application Ready For Evaluation" should {
    "return nothing if there is no PHASE1_TESTS applications" in {
      insertApplication("appId1", ApplicationStatus.SUBMITTED)
      insertApplication("appId2", ApplicationStatus.PHASE2_TESTS)
      insertApplication("appId3", ApplicationStatus.PHASE3_TESTS)
      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return nothing if application does not have online exercise results" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1Tests))
      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return application in PHASE1_TESTS with results" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult))

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue

      result must not be empty
      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE1_TESTS,
        ApplicationRoute.Faststream,
        isGis = false,
        Phase1TestProfile(now, phase1TestsWithResult).activeTests,
        None,
        None,
        selectedSchemes(List(Commercial)))
    }

    "return GIS application in PHASE1_TESTS with results" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult), isGis = true)

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue

      result must not be empty
      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE1_TESTS,
        ApplicationRoute.Faststream,
        isGis = true,
        Phase1TestProfile(now, phase1TestsWithResult).activeTests,
        None,
        None,
        selectedSchemes(List(Commercial)))
    }

    "return nothing when PHASE1_TESTS have expired" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        additionalProgressStatuses = List(ProgressStatuses.PHASE1_TESTS_EXPIRED -> true))

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue

      result mustBe empty
    }

    "limit number of next applications to the batch size limit" in {
      val batchSizeLimit = 5
      1 to 6 foreach { id =>
        insertApplication(s"app$id", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult), isGis = false)
      }
      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSizeLimit).futureValue
      result.size mustBe batchSizeLimit
    }

    "return less number of applications than batch size limit" in {
      val batchSizeLimit = 5
      1 to 2 foreach { id =>
        insertApplication(s"app$id", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult), isGis = false)
      }
      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSizeLimit).futureValue
      result.size mustBe 2
    }
  }

  "save passmark evaluation" should {
    val resultToSave = List(SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString))

    "save result and update the status" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave)

      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ProgressStatuses.PHASE1_TESTS_PASSED)).futureValue

      val resultWithAppStatus = getOnePhase1Profile("app1")
      resultWithAppStatus mustBe defined
      val (appStatus, result) = resultWithAppStatus.get
      appStatus mustBe ApplicationStatus.PHASE1_TESTS_PASSED
      result.evaluation mustBe Some(PassmarkEvaluation("version1", None, List(
        SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString)
      )))
    }

    "return nothing when candidate has been already evaluated" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return the candidate in PHASE1_TESTS if the passmark has changed" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version2", batchSize = 1).futureValue
      result must not be empty
    }

    "return the SdipFaststream candidate in PHASE2_TESTS if the sdip is not evaluated for phase1" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("app1", ProgressStatuses.PHASE2_TESTS_INVITED).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result must not be empty
    }

    "do not return the SdipFaststream candidate in PHASE2_TESTS if the sdip is already evaluated for phase1" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1TestsWithResult),
        applicationRoute = Some(ApplicationRoute.SdipFaststream))

      val resultToSave = List(SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString),
        SchemeEvaluationResult(SchemeType.Sdip, Green.toString))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave)

      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newProgressStatus = None).futureValue
      applicationRepository.addProgressStatusAndUpdateAppStatus("app1", ProgressStatuses.PHASE2_TESTS_INVITED).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }
  }

  private def getOnePhase1Profile(appId: String) = {
    phase1EvaluationRepo.collection.find(BSONDocument("applicationId" -> appId)).one[BSONDocument].map(_.map { doc =>
      val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
      val bsonPhase1 = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument]("PHASE1"))
      val phase1 = bsonPhase1.map(Phase1TestProfile.bsonHandler.read).get
      (applicationStatus, phase1)
    }).futureValue
  }
}

object Phase1EvaluationMongoRepositorySpec {
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
