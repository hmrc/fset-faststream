package repositories.onlinetesting

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Green
import model.OnlineTestCommands.{ Phase1Test, Phase1TestProfile }
import model.SchemeType._
import model.persisted._
import model.{ ApplicationStatus, ProgressStatuses, SchemeType, SelectedSchemes }
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.GeneralApplicationMongoRepository
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import repositories.schemepreferences
import services.GBTimeZoneService
import testkit.MongoRepositorySpec
import config.MicroserviceAppConfig._

class Phase1EvaluationMongoRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._
  import Phase1EvaluationMongoRepositorySpec._

  val collectionName: String = "application"

  def phase1EvaluationRepo = new Phase1EvaluationMongoRepository

  def helperAppRepo = new GeneralApplicationMongoRepository(GBTimeZoneService, cubiksGatewayConfig)
  def helperAssistanceDetailsRepo = new AssistanceDetailsMongoRepository
  def helperPhase1TestMongoRepoo = new Phase1TestMongoRepository(DateTimeFactory)
  def helperSchemePreferencesRepo = new schemepreferences.SchemePreferencesMongoRepository

  "next Application Ready For Evaluation" should {
    "return nothing if there is no PHASE1_TESTS and PHASE2_TESTS applications" in {
      insertApp("appId", ApplicationStatus.SUBMITTED)
      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation("version1").futureValue
      result mustBe None
    }

    "return application in PHASE1_TESTS with results" in {
      insertApp("app1", ApplicationStatus.PHASE1_TESTS, Some(testsWithResult))

      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation("version1").futureValue

      result mustBe Some(ApplicationPhase1ReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE1_TESTS,
        isGis = false,
        Phase1TestProfile(now, testsWithResult),
        selectedSchemes))
    }

    "return GIS application in PHASE1_TESTS with results" in {
      insertApp("app1", ApplicationStatus.PHASE1_TESTS, Some(testsWithResult), isGis = true)

      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation("version1").futureValue

      result mustBe Some(ApplicationPhase1ReadyForEvaluation(
        "app1",
        ApplicationStatus.PHASE1_TESTS,
        isGis = true,
        Phase1TestProfile(now, testsWithResult),
        selectedSchemes))
    }
  }

  "save passmark evaluation" should {
    val resultToSave = List(SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString))

    "save result and update the status" in {
      insertApp("app1", ApplicationStatus.PHASE1_TESTS, Some(testsWithResult))
      val evaluation = PassmarkEvaluation("version1", resultToSave)

      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ApplicationStatus.PHASE1_TESTS_PASSED)).futureValue

      val resultWithAppStatus = getOnePhase1Profile("app1")
      resultWithAppStatus mustBe defined
      val (appStatus, result) = resultWithAppStatus.get
      appStatus mustBe ApplicationStatus.PHASE1_TESTS_PASSED
      result.evaluation mustBe Some(PassmarkEvaluation("version1", List(
        SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString)
      )))
    }

    "return nothing when candidate has been already evaluated" in {
      insertApp("app1", ApplicationStatus.PHASE1_TESTS, Some(testsWithResult))
      val evaluation = PassmarkEvaluation("version1", resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ApplicationStatus.PHASE1_TESTS)).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation("version1").futureValue
      result mustBe None
    }

    "return the candidate in PHASE1_TESTS if the passmark has changed" in {
      insertApp("app1", ApplicationStatus.PHASE1_TESTS, Some(testsWithResult))
      val evaluation = PassmarkEvaluation("version1", resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ApplicationStatus.PHASE1_TESTS)).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation("version2").futureValue
      result mustBe defined
    }


    "return the candidate to re-evaluation in PHASE1_TESTS_PASSED if the passmark has changed" in {
      insertApp("app1", ApplicationStatus.PHASE1_TESTS, Some(testsWithResult))
      val evaluation = PassmarkEvaluation("version1", resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ApplicationStatus.PHASE1_TESTS_PASSED)).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation("version2").futureValue
      result mustBe defined
    }

    "return the candidate to re-evaluation in PHASE2_TESTS if the passmark has changed" in {
      insertApp("app1", ApplicationStatus.PHASE1_TESTS, Some(testsWithResult))
      val evaluation = PassmarkEvaluation("version1", resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, Some(ApplicationStatus.PHASE2_TESTS)).futureValue
      getOnePhase1Profile("app1") mustBe defined

      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation("version2").futureValue
      result mustBe defined
    }

    "do not change application status when it is not required" in {
      insertApp("app1", ApplicationStatus.PHASE2_TESTS, Some(testsWithResult))
      val evaluation = PassmarkEvaluation("version1", resultToSave)
      phase1EvaluationRepo.savePassmarkEvaluation("app1", evaluation, newApplicationStatus = None).futureValue

      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation("version2").futureValue
      result mustBe defined
      result.get.applicationStatus mustBe ApplicationStatus.PHASE2_TESTS
    }
  }

  private def insertApp(appId: String, applicationStatus: ApplicationStatus, tests: Option[List[Phase1Test]] = None,
                        isGis: Boolean = false): Unit = {
    val gis = if (isGis) Some(true) else None
    helperAppRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> appId,
      "applicationStatus" -> applicationStatus
    )).futureValue
    val ad = AssistanceDetails("No", None, gis, needsSupportForOnlineAssessment = false, None, needsSupportAtVenue = false, None)
    helperAssistanceDetailsRepo.update(appId, appId, ad).futureValue
    helperSchemePreferencesRepo.save(appId, selectedSchemes).futureValue

    tests.foreach { t =>
      helperPhase1TestMongoRepoo.insertOrUpdatePhase1TestGroup(appId, Phase1TestProfile(now, t)).futureValue
      t.foreach { oneTest =>
        oneTest.testResult.foreach { result =>
          helperPhase1TestMongoRepoo.insertPhase1TestResult(appId, oneTest, result).futureValue
        }
      }
      if(t.exists(_.testResult.isDefined)) {
        helperPhase1TestMongoRepoo.updateProgressStatus(appId, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).futureValue
      }
    }

    helperAppRepo.collection.update(
      BSONDocument("applicationId" -> appId),
      BSONDocument("$set" -> BSONDocument("applicationStatus" -> applicationStatus))).futureValue
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
  implicit val now = DateTime.now().withZone(DateTimeZone.UTC)
  import model.Phase1TestExamples._

  val phase1Tests = List(firstTest, firstTest)
  val selectedSchemes = SelectedSchemes(List(Commercial, DigitalAndTechnology), orderAgreed = true, eligible = true)
  val testsWithResult = phase1TestsWithResults(TestResult("Ready", "norm", Some(20.5), None, None, None))

  def phase1TestsWithResults(testResult: TestResult) = {
    phase1Tests.map(t => t.copy(testResult = Some(testResult)))
  }
}
