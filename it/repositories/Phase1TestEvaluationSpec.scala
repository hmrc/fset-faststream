package repositories

import config.{ CubiksGatewayConfig, Phase1TestsConfig }
import factories.DateTimeFactory
import model.ApplicationStatus
import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults._
import model.SchemeType._
import model.exchange.passmarksettings.{ PassMarkThreshold, Phase1PassMark, Phase1PassMarkSettings, Phase1PassMarkThresholds }
import model.persisted.SchemeEvaluationResult
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop._
import repositories.application.GeneralApplicationMongoRepository
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import repositories.onlinetesting.{ Phase1EvaluationMongoRepository, Phase1TestMongoRepository }
import services.GBTimeZoneService
import services.onlinetesting.EvaluatePhase1ResultService
import testkit.MongoRepositorySpec

import scala.Double.NaN


class Phase1TestEvaluationSpec extends MongoRepositorySpec with CommonRepository with MockitoSugar
  with TableDrivenPropertyChecks {
  val collectionName = "application"

  val mockGatewayConfig = mock[CubiksGatewayConfig]

  def applicationRepository = new GeneralApplicationMongoRepository(GBTimeZoneService, mockGatewayConfig)

  def schemePreferencesRepository = new schemepreferences.SchemePreferencesMongoRepository

  def assistanceDetailsRepository = new AssistanceDetailsMongoRepository

  def phase1TestRepository = new Phase1TestMongoRepository(DateTimeFactory)

  def phase1PassMarkSettingRepo = new Phase1PassMarkSettingsMongoRepository()

  def phase1EvaluationRepo = new Phase1EvaluationMongoRepository()

  def phase1TestEvaluationService = new EvaluatePhase1ResultService {
    val phase1EvaluationRepository = phase1EvaluationRepo
    val gatewayConfig = mockGatewayConfig
    val phase1PMSRepository = phase1PassMarkSettingRepo
    val phase1TestsConfigMock = mock[Phase1TestsConfig]

    when(gatewayConfig.phase1Tests).thenReturn(phase1TestsConfigMock)
    when(phase1TestsConfigMock.scheduleIds).thenReturn(Map("sjq" -> 1, "bq" -> 2))
  }

  // format: OFF
  val phase1PassMarkSettingsTable = Table[SchemeType, Double, Double, Double, Double](
    ("Scheme Name",                       "SJQ Fail Threshold",   "SJQ Pass threshold",   "BQ Fail Threshold",    "BQ Pass Threshold"),
    (Commercial,                            20.0,                    80.0,                   30.0,                   70.0),
    (DigitalAndTechnology,                  20.001,                  20.001,                 20.01,                  20.05),
    (DiplomaticService,                     20.01,                   20.02,                  20.01,                  20.02),
    (DiplomaticServiceEconomics,            30.0,                    70.0,                   30.0,                   70.0),
    (DiplomaticServiceEuropean,             30.0,                    70.0,                   30.0,                   70.0),
    (European,                              40.0,                    70.0,                   30.0,                   70.0),
    (Finance,                               30.0,                    70.0,                   30.0,                   70.0),
    (Generalist,                            30.0,                    70.0,                   30.0,                   70.0),
    (GovernmentCommunicationService,        30.0,                    70.0,                   30.0,                   70.0),
    (GovernmentEconomicsService,            30.0,                    70.0,                   30.0,                   70.0),
    (GovernmentOperationalResearchService,  30.0,                    70.0,                   30.0,                   70.0),
    (GovernmentSocialResearchService,       30.0,                    70.0,                   30.0,                   70.0),
    (GovernmentStatisticalService,          30.0,                    70.0,                   30.0,                   70.0),
    (HousesOfParliament,                    30.0,                    79.999,                 30.0,                   78.08),
    (HumanResources,                        30.0,                    70.0,                   30.0,                   70.0),
    (ProjectDelivery,                       30.0,                    70.0,                   30.0,                   70.0),
    (ScienceAndEngineering,                 69.00,                   69.00,                  78.99,                  78.99)
  )
  // format: ON

  def applicationTable(application: (String, Double, Double, Boolean, List[SchemeType])*) = Table(
    ("ApplicationId", "SJQ Score", "BQ Score", "isGis", "Selected Schemes"),
    application: _*)

  def expectedResultTable(expectedResult: (String, ApplicationStatus, List[(SchemeType, Result)])*) = Table(
    ("Application Id", "Application Status", "Expected schemes And Results"),
    expectedResult: _*)

  "phase1 evaluation process" should {

    "give pass results when all schemes are passed" in {
      val applications = applicationTable(
        ("application-1", 80, 80, false, List(Commercial, DigitalAndTechnology)),
        ("application-2", 79.999, 78.08, false, List(HousesOfParliament))
      )
      val expectedResults = expectedResultTable(
        ("application-1", PHASE1_TESTS_PASSED, List(Commercial -> Green, DigitalAndTechnology -> Green)),
        ("application-2", PHASE1_TESTS_PASSED, List(HousesOfParliament -> Green))
      )
      evaluatePhase1Tests(applications)(expectedResults)
    }

    "give pass results when at-least one scheme is passed" in {
      val applications = applicationTable(
        ("application-1", 20.002, 20.06, false, List(Commercial, DigitalAndTechnology))
      )
      val expectedResults = expectedResultTable(
        ("application-1", PHASE1_TESTS_PASSED, List(Commercial -> Red, DigitalAndTechnology -> Green))
      )
      evaluatePhase1Tests(applications)(expectedResults)
    }

    "give fail results when none of the schemes are passed" in {
      val applications = applicationTable(
        ("application-1", 20, 20, false, List(DiplomaticServiceEconomics, DiplomaticServiceEuropean))
      )
      val expectedResults = expectedResultTable(
        ("application-1", PHASE1_TESTS_FAILED, List(DiplomaticServiceEconomics -> Red, DiplomaticServiceEuropean -> Red))
      )
      evaluatePhase1Tests(applications)(expectedResults)
    }

    "leave applicants in amber when all the schemes are in amber" in {
      val applications = applicationTable(
        ("application-1", 40, 40, false, List(DiplomaticServiceEconomics, DiplomaticServiceEuropean))
      )
      val expectedResults = expectedResultTable(
        ("application-1", PHASE1_TESTS, List(DiplomaticServiceEconomics -> Amber, DiplomaticServiceEuropean -> Amber))
      )
      evaluatePhase1Tests(applications)(expectedResults)
    }

    "leave applicants in amber when at-least one of the scheme is amber and none of the schemes in green" in {
      val applications = applicationTable(
        ("application-1", 30, 80, false, List(Commercial, European))
      )
      val expectedResults = expectedResultTable(
        ("application-1", PHASE1_TESTS, List(Commercial -> Amber, European -> Red))
      )
      evaluatePhase1Tests(applications)(expectedResults)
    }

    "give pass results for gis candidates" in {
      val applications = applicationTable(
        ("application-1", 25, NaN, true, List(Commercial, DigitalAndTechnology))
      )
      val expectedResults = expectedResultTable(
        ("application-1", PHASE1_TESTS_PASSED, List(Commercial -> Amber, DigitalAndTechnology -> Green))
      )
      evaluatePhase1Tests(applications)(expectedResults)
    }
  }

  private def createPhase1PassMarkSettings(): Phase1PassMarkSettings = {
    val schemeThresholds = phase1PassMarkSettingsTable.map {
      fields => Phase1PassMark(fields._1,
        Phase1PassMarkThresholds(PassMarkThreshold(fields._2, fields._3), PassMarkThreshold(fields._4, fields._5)))
    }.toList

    val phase1PassMarkSettings = Phase1PassMarkSettings(
      schemeThresholds,
      "version-1",
      DateTime.now,
      "user-1"
    )
    phase1PassMarkSettingRepo.create(phase1PassMarkSettings).futureValue
    phase1PassMarkSettingRepo.getLatestVersion.futureValue.get
  }

  private def evaluatePhase1Tests(applications: TableFor5[String, Double, Double, Boolean, List[SchemeType]])
                                 (expectedPhase1Results: TableFor3[String, ApplicationStatus, List[(SchemeType, Result)]]) = {

    val phase1PassMarkSettings = createPhase1PassMarkSettings()

    val actualPhase1Results = applications.map {
      case (applicationId, sjqScore, bqScore, isGis, selectedSchemes) =>
        val optBqScore = if (bqScore.isNaN) None else Some(bqScore)

        val applicationReadyForEvaluation = insertApplicationWithPhase1TestResults(applicationId, sjqScore, optBqScore,
          isGis)(selectedSchemes: _*)

        phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, phase1PassMarkSettings).futureValue

        val passMarkEvaluation = phase1EvaluationRepo.getPassMarkEvaluation(applicationId).futureValue

        val applicationStatus = ApplicationStatus.withName(applicationRepository.findStatus(applicationId).futureValue.status)

        val schemeResults = passMarkEvaluation.result.map {
          SchemeEvaluationResult.unapply(_).map {
            case (schemeType, resultStr) => schemeType -> Result(resultStr)
          }.get
        }

        (applicationId, applicationStatus, schemeResults)
    }

    actualPhase1Results must contain theSameElementsAs expectedPhase1Results

  }

}
