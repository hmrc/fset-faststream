package repositories

import config.Phase1TestsConfig
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults._
import model.{ ApplicationRoute, ApplicationStatus, Phase, SchemeId }
import model.exchange.passmarksettings.{ PassMarkThreshold, Phase1PassMark, Phase1PassMarkSettings, Phase1PassMarkThresholds }
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult }
import model.ProgressStatuses
import model.ProgressStatuses.ProgressStatus
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.prop._
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import reactivemongo.json.collection.JSONCollection
import services.onlinetesting.phase1.EvaluatePhase1ResultService
import testkit.MongoRepositorySpec

import scala.concurrent.Future


class Phase1TestEvaluationSpec extends MongoRepositorySpec with CommonRepository
  with TableDrivenPropertyChecks {
  import ImplicitBSONHandlers._

  val collectionName = CollectionNames.APPLICATION
  override val additionalCollections = List(CollectionNames.PHASE1_PASS_MARK_SETTINGS)

  def phase1TestEvaluationService = new EvaluatePhase1ResultService {
    val evaluationRepository = phase1EvaluationRepo
    val gatewayConfig = mockGatewayConfig
    val passMarkSettingsRepo = phase1PassMarkSettingRepo
    val phase1TestsConfigMock = mock[Phase1TestsConfig]
    val phase = Phase.PHASE1

    when(gatewayConfig.phase1Tests).thenReturn(phase1TestsConfigMock)
    when(phase1TestsConfigMock.scheduleIds).thenReturn(Map("sjq" -> 16196, "bq" -> 16194))
  }

  "phase1 evaluation process" should {

    "give pass results when all schemes are passed" in new TestFixture {

        applicationEvaluation("application-1", 80, 80,SchemeId("Commercial"), SchemeId("DigitalAndTechnology")) mustResultIn (
          PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
          SchemeId("Commercial") -> Green, SchemeId("DigitalAndTechnology") -> Green)

        applicationEvaluation("application-2", 79.999, 78.08,SchemeId("HousesOfParliament")) mustResultIn (
          PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED), SchemeId("HousesOfParliament") -> Green)

        applicationEvaluation("application-3", 30, 30,SchemeId("Generalist")) mustResultIn (
          PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED), SchemeId("Generalist") -> Green)
    }

    "give pass results when at-least one scheme is passed" in new TestFixture {

      applicationEvaluation("application-1", 20.002, 20.06,SchemeId("Commercial"), SchemeId("DigitalAndTechnology")) mustResultIn (
        PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Red, SchemeId("DigitalAndTechnology") -> Green)
    }

    "give fail results when none of the schemes are passed" in new TestFixture {

      applicationEvaluation("application-1", 20, 20,SchemeId("DiplomaticServiceEconomics"), SchemeId("DiplomaticServiceEuropean")) mustResultIn (
        PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED),
        SchemeId("DiplomaticServiceEconomics") -> Red, SchemeId("DiplomaticServiceEuropean") -> Red)
    }

    "leave applicants in amber when all the schemes are in amber" in new TestFixture {

      applicationEvaluation("application-1", 40, 40,SchemeId("DiplomaticServiceEconomics"), SchemeId("DiplomaticServiceEuropean")) mustResultIn (
        PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
        SchemeId("DiplomaticServiceEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

      applicationEvaluation("application-2", 25.015, 25.015, SchemeId("Finance")) mustResultIn (
        PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), SchemeId("Finance") -> Amber)
    }

    "leave applicants in amber when at-least one of the scheme is amber and none of the schemes in green" in new TestFixture {

      applicationEvaluation("application-1", 30, 80,SchemeId("Commercial"), SchemeId("European")) mustResultIn (
        PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
        SchemeId("Commercial") -> Amber, SchemeId("European") -> Red)
    }

    "give pass results for gis candidates" in new TestFixture {

      gisApplicationEvaluation("application-1", 25,SchemeId("Commercial"), SchemeId("DigitalAndTechnology")) mustResultIn (
        PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Amber, SchemeId("DigitalAndTechnology") -> Green)
    }

    "re-evaluate to green applicants in amber when passmarks are decreased" in new TestFixture {
      {
        applicationEvaluation("application-1", 40, 40,SchemeId("DiplomaticServiceEconomics"), SchemeId("DiplomaticServiceEuropean"))
          mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
            SchemeId("DiplomaticServiceEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("DiplomaticServiceEconomics"), 30, 30, 30, 30),
          (SchemeId("DiplomaticServiceEuropean"), 30, 30, 30, 30))
        mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
          SchemeId("DiplomaticServiceEconomics") -> Green, SchemeId("DiplomaticServiceEuropean") -> Green)
      }

      {
        applicationEvaluation("application-2", 25.015, 25.015, SchemeId("Finance")) mustResultIn (
          PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), SchemeId("Finance") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("Finance"), 25.011, 25.014, 25.011, 25.014)
        ) mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED), SchemeId("Finance") -> Green)

      }

    }

    "re-evaluate to red applicants in amber when failmarks are increased" in new TestFixture {

      {
        applicationEvaluation("application-1", 40, 40,SchemeId("DiplomaticServiceEconomics"), SchemeId("DiplomaticServiceEuropean"))
        mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
          SchemeId("DiplomaticServiceEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("DiplomaticServiceEconomics"), 41, 42, 41, 42),
          (SchemeId("DiplomaticServiceEuropean"), 41, 42, 41, 42)
        ) mustResultIn (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED),
          SchemeId("DiplomaticServiceEconomics") -> Red, SchemeId("DiplomaticServiceEuropean") -> Red)
      }

      {
        applicationEvaluation("application-2", 25.015, 25.015, SchemeId("Finance")) mustResultIn (
          PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), SchemeId("Finance") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("Finance"), 26.015, 27.015, 26.015, 27.015)
        ) mustResultIn (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED), SchemeId("Finance") -> Red)

      }

    }

    "re-evaluate to amber applicants in amber when passmarks and failmarks are changed but within the amber range" in new TestFixture {

      {
        applicationEvaluation("application-1", 40, 40,SchemeId("DiplomaticServiceEconomics"), SchemeId("DiplomaticServiceEuropean"))
        mustResultIn (PHASE1_TESTS,  Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
          SchemeId("DiplomaticServiceEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("DiplomaticServiceEconomics"), 38, 42, 38, 42), (SchemeId("DiplomaticServiceEuropean"), 38, 42, 38, 42)
        ) mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
          SchemeId("DiplomaticServiceEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)
      }

      {
        applicationEvaluation("application-2", 25.015, 25.015, SchemeId("Finance")) mustResultIn (
          PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), SchemeId("Finance") -> Amber)

        applicationReEvaluationWithOverridingPassmarks(
          (SchemeId("Finance"), 24.015, 27.015, 24.015, 27.015)
        ) mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED), SchemeId("Finance") -> Amber)

      }

    }

    "evaluate sdip scheme to Green for SdipFaststream candidate" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable, (SchemeId("Sdip"), 30.00, 70.00, 30.00, 70.00))
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 80, 80,SchemeId("Commercial"),
        SchemeId("DigitalAndTechnology"), SchemeId("Sdip"))(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Green, SchemeId("DigitalAndTechnology") -> Green, SchemeId("Sdip") -> Green)
    }

    "evaluate sdip scheme to Amber for SdipFaststream candidate" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable, (SchemeId("Sdip"), 30.00, 70.00, 30.00, 70.00),
        (SchemeId("DigitalAndTechnology"), 30.00, 70.00, 30.00, 70.00))
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 40, 40,SchemeId("Commercial"),
        SchemeId("DigitalAndTechnology"), SchemeId("Sdip"))(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED),
        SchemeId("Commercial") -> Amber, SchemeId("DigitalAndTechnology") -> Amber, SchemeId("Sdip") -> Amber)
    }

    "evaluate sdip scheme to Red for SdipFaststream candidate" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable, (SchemeId("Sdip"), 30.00, 70.00, 30.00, 70.00))
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 20, 20,SchemeId("Commercial"),
        SchemeId("DigitalAndTechnology"), SchemeId("Sdip"))(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED),
        SchemeId("Commercial") -> Red, SchemeId("DigitalAndTechnology") -> Red, SchemeId("Sdip") -> Red)
    }


    "re-evaluate sdip scheme to Red for SdipFaststream candidate after changing passmarks" in new TestFixture {
      applicationEvaluation("application-1", 80, 80,SchemeId("Commercial"), SchemeId("DigitalAndTechnology"), SchemeId("Sdip"))
        (ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Green, SchemeId("DigitalAndTechnology") -> Green)

      applicationReEvaluationWithOverridingPassmarks(
        (SchemeId("Sdip"), 90.00, 90.00, 90.00, 90.00)
      ) mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Green, SchemeId("DigitalAndTechnology") -> Green, SchemeId("Sdip") -> Red)
    }

    "re-evaluate sdip scheme to Green for SdipFaststream candidate after changing passmarks" in new TestFixture {
      applicationEvaluation("application-1", 80, 80,SchemeId("Commercial"), SchemeId("DigitalAndTechnology"), SchemeId("Sdip"))
        (ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Green, SchemeId("DigitalAndTechnology") -> Green)

      applicationReEvaluationWithOverridingPassmarks( (SchemeId("Sdip"), 80.00, 80.00, 80.00, 80.00) )
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Green, SchemeId("DigitalAndTechnology") -> Green, SchemeId("Sdip") -> Green)
    }

    "do not evaluate sdip scheme for SdipFaststream candidate until there are sdip passmarks" in new TestFixture {
      applicationEvaluation("application-1", 80, 80,SchemeId("Commercial"), SchemeId("DigitalAndTechnology"), SchemeId("Sdip"))
        (ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Green, SchemeId("DigitalAndTechnology") -> Green)

      applicationReEvaluationWithOverridingPassmarks( (SchemeId("Sdip"), 40.00, 40.00, 40.00, 40.00) )
      mustResultIn (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED),
        SchemeId("Commercial") -> Green, SchemeId("DigitalAndTechnology") -> Green, SchemeId("Sdip") -> Green)
    }


    "progress candidate to PHASE1_TESTS_PASSED with faststream schemes in RED and sdip in GREEN " +
      "when candidate is in sdipFaststream route and only sdip scheme score is passing the passmarks" in new TestFixture {
      val passmarksTable = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable,
        (SchemeId("Sdip"), 30.00, 50.00, 30.00, 50.00),
        (SchemeId("Commercial"), 75.00, 75.00, 75.00, 75.00),
        (SchemeId("DigitalAndTechnology"), 75.00, 75.00, 75.00, 75.00))
      phase1PassMarkSettings = createPhase1PassMarkSettings(passmarksTable).futureValue

      applicationEvaluationWithPassMarks(phase1PassMarkSettings, "application-1", 60, 60,
        SchemeId("Commercial"), SchemeId("DigitalAndTechnology"), SchemeId("Sdip"))(ApplicationRoute.SdipFaststream)
      mustResultIn (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_FAILED_SDIP_NOT_FAILED),
        SchemeId("Commercial") -> Red, SchemeId("DigitalAndTechnology") -> Red, SchemeId("Sdip") -> Green)
    }
  }

  trait TestFixture {

    // format: OFF
    val phase1PassMarkSettingsTable = Table[SchemeId, Double, Double, Double, Double](
      ("Scheme Name",                       "SJQ Fail Threshold",   "SJQ Pass threshold",   "BQ Fail Threshold",    "BQ Pass Threshold"),
      (SchemeId("Commercial"),                            20.0,                    80.0,                   30.0,                   70.0),
      (SchemeId("DigitalAndTechnology"),                  20.001,                  20.001,                 20.01,                  20.05),
      (SchemeId("DiplomaticService"),                     20.01,                   20.02,                  20.01,                  20.02),
      (SchemeId("DiplomaticServiceEconomics"),            30.0,                    70.0,                   30.0,                   70.0),
      (SchemeId("DiplomaticServiceEuropean"),             30.0,                    70.0,                   30.0,                   70.0),
      (SchemeId("European"),                              40.0,                    70.0,                   30.0,                   70.0),
      (SchemeId("Finance"),                               25.01,                   25.02,                  25.01,                  25.02),
      (SchemeId("Generalist"),                            30.0,                    30.0,                   30.0,                   30.0),
      (SchemeId("GovernmentCommunicationService"),        30.0,                    70.0,                   30.0,                   70.0),
      (SchemeId("GovernmentEconomicsService"),            30.0,                    70.0,                   30.0,                   70.0),
      (SchemeId("GovernmentOperationalResearchService"),  30.0,                    70.0,                   30.0,                   70.0),
      (SchemeId("GovernmentSocialResearchService"),       30.0,                    70.0,                   30.0,                   70.0),
      (SchemeId("GovernmentStatisticalService"),          30.0,                    70.0,                   30.0,                   70.0),
      (SchemeId("HousesOfParliament"),                    30.0,                    79.999,                 30.0,                   78.08),
      (SchemeId("HumanResources"),                        30.0,                    70.0,                   30.0,                   70.0),
      (SchemeId("ProjectDelivery"),                       30.0,                    70.0,                   30.0,                   70.0),
      (SchemeId("ScienceAndEngineering"),                 69.00,                   69.00,                  78.99,                  78.99)
    )

    val phase1PassMarkSettingWithSdipTable =
      getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable, (SchemeId("Finance"), 90.00, 90.00, 90.00, 90.00))
    // format: ON

    var phase1PassMarkSettings: Phase1PassMarkSettings = _

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation = _

    var passMarkEvaluation: PassmarkEvaluation = _

    def gisApplicationEvaluation(applicationId:String, sjqScore: Double, selectedSchemes: SchemeId*): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase1TestResults(applicationId, sjqScore, None, isGis = true)(selectedSchemes: _*)
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, phase1PassMarkSettings).futureValue
      this
    }

    def applicationEvaluationWithPassMarks(passmarks: Phase1PassMarkSettings, applicationId:String, sjqScore: Double, bjqScore: Double,
      selectedSchemes: SchemeId*)(implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase1TestResults(applicationId, sjqScore, Some(bjqScore),
        isGis = false, applicationRoute = applicationRoute)(selectedSchemes: _*)
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, passmarks).futureValue
      this
    }

    def applicationEvaluation(applicationId: String, sjqScore: Double, bjqScore: Double, selectedSchemes: SchemeId*)
      (implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationEvaluationWithPassMarks(phase1PassMarkSettings, applicationId, sjqScore, bjqScore, selectedSchemes:_*)
    }

    def mustResultIn(expApplicationStatus: ApplicationStatus, expProgressStatus: Option[ProgressStatus],
      expSchemeResults: (SchemeId , Result)*): TestFixture = {
      passMarkEvaluation = phase1EvaluationRepo.getPassMarkEvaluation(applicationReadyForEvaluation.applicationId).futureValue
      val applicationDetails = applicationRepository.findStatus(applicationReadyForEvaluation.applicationId).futureValue
      val applicationStatus = ApplicationStatus.withName(applicationDetails.status)
      val progressStatus = applicationDetails.latestProgressStatus

      val schemeResults = passMarkEvaluation.result.map {
        SchemeEvaluationResult.unapply(_).map {
          case (schemeType, resultStr) => schemeType -> Result(resultStr)
        }.get
      }
      phase1PassMarkSettings.version mustBe passMarkEvaluation.passmarkVersion
      applicationStatus mustBe expApplicationStatus
      progressStatus mustBe expProgressStatus
      schemeResults.size mustBe expSchemeResults.size
      schemeResults must contain theSameElementsAs expSchemeResults
      applicationReadyForEvaluation = applicationReadyForEvaluation.copy(applicationStatus = expApplicationStatus)
      this
    }

    def getPassMarkSettingWithNewSettings(
      phase1PassMarkSettingsTable: TableFor5[SchemeId, Double, Double, Double, Double],
      newSchemeSettings: (SchemeId, Double, Double, Double, Double)*) = {
      phase1PassMarkSettingsTable.filterNot(schemeSetting =>
        newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
    }

    def applicationReEvaluationWithOverridingPassmarks(newSchemeSettings: (SchemeId, Double, Double, Double, Double)*): TestFixture = {
      val schemePassMarkSettings = getPassMarkSettingWithNewSettings(phase1PassMarkSettingsTable, newSchemeSettings:_*)
      phase1PassMarkSettings = createPhase1PassMarkSettings(schemePassMarkSettings).futureValue
      phase1TestEvaluationService.evaluate(applicationReadyForEvaluation, phase1PassMarkSettings).futureValue
      this
    }

    def createPhase1PassMarkSettings(phase1PassMarkSettingsTable:
                                             TableFor5[SchemeId, Double, Double, Double, Double]): Future[Phase1PassMarkSettings] = {
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
      phase1PassMarkSettingRepo.create(phase1PassMarkSettings).flatMap { _ =>
        phase1PassMarkSettingRepo.getLatestVersion.map(_.get)
      }
    }

    val appCollection = mongo().collection[JSONCollection](collectionName)

    def createUser(userId: String, appId: String) = {
      appCollection.insert(BSONDocument("applicationId" -> appId, "userId" -> userId, "applicationStatus" -> CREATED))
    }

    Future.sequence(List(
      createUser("user-1", "application-1"),
      createUser("user-2", "application-2"),
      createUser("user-3", "application-3"),
      createPhase1PassMarkSettings(phase1PassMarkSettingsTable).map(phase1PassMarkSettings = _)
    )).futureValue
  }

}
