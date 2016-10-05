package repositories.onlinetesting

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.OnlineTestCommands.{ Phase1Test, Phase1TestProfile }
import model.SchemeType._
import model.persisted.{ ApplicationToPhase1Evaluation, AssistanceDetails, TestResult }
import model.{ ApplicationStatus, SelectedSchemes }
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.GeneralApplicationMongoRepository
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import repositories.schemepreferences
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

class Phase1EvaluationMongoRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._
  import Phase1EvaluationMongoRepositorySpec._

  val collectionName: String = "application"

  def phase1EvaluationRepo = new Phase1EvaluationMongoRepository

  def helperAppRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)
  def helperAssistanceDetailsRepo = new AssistanceDetailsMongoRepository
  def helperPhase1TestMongoRepoo = new Phase1TestMongoRepository(DateTimeFactory)
  def helperSchemePreferencesRepo = new schemepreferences.SchemePreferencesMongoRepository

  "next Application Ready For Evaluation" should {
    "return nothing if there is no PHASE1_TESTS and PHASE2_TESTS applications" in {
      insertApp("appId", ApplicationStatus.SUBMITTED)
      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation.futureValue
      result mustBe None
    }

    "return nothing if application does not have online exercise results" in {
      insertApp("app1", ApplicationStatus.PHASE1_TESTS, Some(phase1Tests))
      insertApp("app2", ApplicationStatus.PHASE2_TESTS, Some(phase1Tests))

      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation.futureValue

      result mustBe None
    }

    "return application in PHASE1_TESTS with results" in {
      insertApp("app1", ApplicationStatus.PHASE1_TESTS, Some(testsWithResult))

      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation.futureValue

      result mustBe Some(ApplicationToPhase1Evaluation(
        "app1",
        isGis = false,
        Phase1TestProfile(now, testsWithResult),
        selectedSchemes))
    }

    "return GIS application in PHASE1_TESTS with results" in {
      insertApp("app1", ApplicationStatus.PHASE1_TESTS, Some(testsWithResult), isGis = true)

      val result = phase1EvaluationRepo.nextApplicationReadyForPhase1ResultEvaluation.futureValue

      result mustBe Some(ApplicationToPhase1Evaluation(
        "app1",
        isGis = true,
        Phase1TestProfile(now, testsWithResult),
        selectedSchemes))
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
        oneTest.testResult.map { result =>
          helperPhase1TestMongoRepoo.insertPhase1TestResult(appId, oneTest, result)
        }
      }
    }

    helperAppRepo.collection.update(
      BSONDocument("applicationId" -> appId),
      BSONDocument("$set" -> BSONDocument("applicationStatus" -> applicationStatus))).futureValue
  }

}

object Phase1EvaluationMongoRepositorySpec {
  val now = DateTime.now().withZone(DateTimeZone.UTC)
  val phase1Tests = List(
    Phase1Test(1, usedForResults = true, 100, "cubiks", "token1", "http://localhost", now, 2000),
    Phase1Test(2, usedForResults = true, 101, "cubiks", "token2", "http://localhost", now, 2001)
  )
  val selectedSchemes = SelectedSchemes(List(Commercial, DigitalAndTechnology), orderAgreed = true, eligible = true)
  val testsWithResult = phase1TestsWithResults(TestResult("Ready", "norm", Some(20.5), None, None, None))

  def phase1TestsWithResults(testResult: TestResult) = {
    phase1Tests.map(t => t.copy(testResult = Some(testResult)))
  }
}
