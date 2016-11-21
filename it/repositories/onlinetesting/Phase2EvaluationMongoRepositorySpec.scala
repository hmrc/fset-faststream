package repositories.onlinetesting

import config.CubiksGatewayConfig
import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Green
import model.SchemeType._
import model.persisted.{ ApplicationReadyForEvaluation, _ }
import model.{ ApplicationStatus, SchemeType }
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatest.mock.MockitoSugar
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.{ GeneralApplicationMongoRepository }
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import repositories.{ CommonRepository, schemepreferences }
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

class Phase2EvaluationMongoRepositorySpec extends MongoRepositorySpec with CommonRepository with MockitoSugar {

  import ImplicitBSONHandlers._
  import Phase1EvaluationMongoRepositorySpec._
  import Phase2EvaluationMongoRepositorySpec._

  val collectionName: String = "application"

  def phase2EvaluationRepo = new Phase2EvaluationMongoRepository

  def phase1EvaluationRepo = new Phase1EvaluationMongoRepository

  def applicationRepository = new GeneralApplicationMongoRepository(GBTimeZoneService, mock[CubiksGatewayConfig])

  def schemePreferencesRepository = new schemepreferences.SchemePreferencesMongoRepository

  def assistanceDetailsRepository = new AssistanceDetailsMongoRepository

  def phase1TestRepository = new Phase1TestMongoRepository(DateTimeFactory)

  def phase2TestRepository = new Phase2TestMongoRepository(DateTimeFactory)

  "next Application Ready For Evaluation" should {

    val resultToSave = List(SchemeEvaluationResult(SchemeType.Commercial, Green.toString))

    "return nothing if application does not have PHASE2_TESTS" in {
      insertApplication("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1Tests))
      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return application in PHASE2_TESTS with results" in {
      insertApplication("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult), Some(phase2TestWithResult))
      val phase1Evaluation = PassmarkEvaluation("phase1_version1", None, resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", phase1Evaluation, None).futureValue

      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase1_version1", batchSize = 1).futureValue

      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE2_TESTS,
        isGis = false,
        Phase2TestGroup(now, phase2TestWithResult).activeTests,
        Some(phase1Evaluation),
        selectedSchemes(List(Commercial)))
    }

    "return nothing when PHASE2_TESTS are already evaluated" in {
      insertApplication("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult), Some(phase2TestWithResult))
      val phase1Evaluation = PassmarkEvaluation("phase1_version1", None, resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", phase1Evaluation, None).futureValue

      val phase2Evaluation = PassmarkEvaluation("phase2_version1", Some("phase1_version1"), resultToSave)
      phase2EvaluationRepo.savePassmarkEvaluation("app1", phase2Evaluation, None).futureValue

      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase2_version1", batchSize = 1).futureValue
      result mustBe empty
    }

    "return evaluated application in PHASE2_TESTS_PASSED when phase2 pass mark settings changed" in {
      insertApplication("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult), Some(phase2TestWithResult))
      val phase1Evaluation = PassmarkEvaluation("phase1_version1", None, resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", phase1Evaluation, None).futureValue

      val phase2Evaluation = PassmarkEvaluation("phase2_version1", Some("phase1_version1"), resultToSave)
      phase2EvaluationRepo.savePassmarkEvaluation("app1", phase2Evaluation, None).futureValue

      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase2_version2", batchSize = 1).futureValue
      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE2_TESTS,
        isGis = false,
        Phase2TestGroup(now, phase2TestWithResult).activeTests,
        Some(phase1Evaluation),
        selectedSchemes(List(Commercial)))
    }

    "return evaluated application in PHASE2_TESTS status when phase1 results are re-evaluated" in {
      insertApplication("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult), Some(phase2TestWithResult))
      val phase1Evaluation = PassmarkEvaluation("phase1_version2", None, resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", phase1Evaluation, None).futureValue

      val phase2Evaluation = PassmarkEvaluation("phase2_version1", Some("phase1_version1"), resultToSave)
      phase2EvaluationRepo.savePassmarkEvaluation("app1", phase2Evaluation, None).futureValue

      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase2_version1", batchSize = 1).futureValue
      result.head mustBe ApplicationReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE2_TESTS,
        isGis = false,
        Phase2TestGroup(now, phase2TestWithResult).activeTests,
        Some(phase1Evaluation),
        selectedSchemes(List(Commercial)))
    }

    "limit number of next applications to the batch size limit" in {
      val batchSizeLimit = 5
      1 to 6 foreach { id =>
        insertApplication(s"app$id", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult), Some(phase2TestWithResult), isGis = false)
        val phase1Evaluation = PassmarkEvaluation("phase1_version1", None, resultToSave)
        phase1EvaluationRepo.savePassmarkEvaluation(s"app$id", phase1Evaluation, None).futureValue
      }
      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("phase2_version1", batchSizeLimit).futureValue
      result.size mustBe batchSizeLimit
    }

    "return less number of applications than batch size limit" in {
      val batchSizeLimit = 5
      1 to 2 foreach { id =>
        insertApplication(s"app$id", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult), Some(phase2TestWithResult), isGis = false)
        val phase1Evaluation = PassmarkEvaluation("phase1_version1", None, resultToSave)
        phase1EvaluationRepo.savePassmarkEvaluation(s"app$id", phase1Evaluation, None).futureValue
      }
      val result = phase2EvaluationRepo.nextApplicationsReadyForEvaluation("version1", batchSizeLimit).futureValue
      result.size mustBe 2
    }
  }

  "save passmark evaluation" should {
    val resultToSave = List(SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString))

    "save result and update the status" in {
      insertApplication("app1", ApplicationStatus.PHASE2_TESTS, Some(phase1TestsWithResult), Some(phase2TestWithResult))
      val evaluation = PassmarkEvaluation("version1", None, resultToSave)

      phase2EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ApplicationStatus.PHASE2_TESTS_PASSED)).futureValue

      val resultWithAppStatus = getOnePhase2Profile("app1")
      resultWithAppStatus mustBe defined
      val (appStatus, result) = resultWithAppStatus.get
      appStatus mustBe ApplicationStatus.PHASE2_TESTS_PASSED
      result.evaluation mustBe Some(PassmarkEvaluation("version1", None, List(
        SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString)
      )))
    }
  }

  private def getOnePhase2Profile(appId: String) = {
    phase2EvaluationRepo.collection.find(BSONDocument("applicationId" -> appId)).one[BSONDocument].map(_.map { doc =>
      val applicationStatus = doc.getAs[ApplicationStatus]("applicationStatus").get
      val bsonPhase2 = doc.getAs[BSONDocument]("testGroups").flatMap(_.getAs[BSONDocument]("PHASE2"))
      val phase2 = bsonPhase2.map(Phase2TestGroup.bsonHandler.read).get
      (applicationStatus, phase2)
    }).futureValue
  }
}

object Phase2EvaluationMongoRepositorySpec {
  val now = DateTime.now().withZone(DateTimeZone.UTC)
  val phase2Test = List(CubiksTest(16196, usedForResults = true, 100, "cubiks", "token1", "http://localhost", now, 2000))
  val phase2TestWithResult = phase2TestWithResults(TestResult("Ready", "norm", Some(20.5), None, None, None))

  def phase2TestWithResults(testResult: TestResult) = {
    phase2Test.map(t => t.copy(testResult = Some(testResult)))
  }
}


