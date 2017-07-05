package repositories

import config.Phase2TestsConfig
import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults.{ Amber, _ }
import model.SchemeId._
import model.exchange.passmarksettings._
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationStatus, Phase }
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.prop._
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import reactivemongo.json.collection.JSONCollection
import services.onlinetesting.phase2.EvaluatePhase2ResultService
import testkit.MongoRepositorySpec

import scala.concurrent.Future

class Phase2TestEvaluationSpec extends MongoRepositorySpec with CommonRepository
  with TableDrivenPropertyChecks {

  import ImplicitBSONHandlers._

  val collectionName = CollectionNames.APPLICATION
  override val additionalCollections = List("phase2-pass-mark-settings")

  def phase2TestEvaluationService = new EvaluatePhase2ResultService {
    val evaluationRepository = phase2EvaluationRepo
    val gatewayConfig = mockGatewayConfig
    val passMarkSettingsRepo = phase2PassMarkSettingRepo
    val phase2TestsConfigMock = mock[Phase2TestsConfig]
    val phase = Phase.PHASE2
    when(gatewayConfig.phase2Tests).thenReturn(phase2TestsConfigMock)
  }

  "phase2 evaluation process" should {

    "give pass results when all schemes are green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString),
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 80,SchemeId("Commercial"), SchemeId("DigitalAndTechnology")) mustResultIn(
          PHASE2_TESTS_PASSED, SchemeId("Commercial") -> Green, SchemeId("DigitalAndTechnology") -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("HousesOfParliament"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 79.999,SchemeId("HousesOfParliament")) mustResultIn(
          PHASE2_TESTS_PASSED, SchemeId("HousesOfParliament") -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("Generalist"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-3", 30,SchemeId("Generalist")) mustResultIn(
          PHASE2_TESTS_PASSED, SchemeId("Generalist") -> Green)
      }
    }
    "give pass results when at-least one scheme is green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("Commercial"), Red.toString),
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 80,SchemeId("Commercial"), SchemeId("DigitalAndTechnology")) mustResultIn(
          PHASE2_TESTS_PASSED, SchemeId("Commercial") -> Red, SchemeId("DigitalAndTechnology") -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("HumanResources"), Green.toString),
          SchemeEvaluationResult(SchemeId("ProjectDelivery"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 50,SchemeId("HumanResources"), SchemeId("ProjectDelivery")) mustResultIn(
          PHASE2_TESTS_PASSED, SchemeId("HumanResources") -> Green, SchemeId("ProjectDelivery") -> Amber)
      }
    }
    "give fail results when all the schemes are red" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Green.toString),
          SchemeEvaluationResult(SchemeId("ScienceAndEngineering"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 35,SchemeId("European"), SchemeId("ScienceAndEngineering")) mustResultIn(
          PHASE2_TESTS_FAILED, SchemeId("European") -> Red, SchemeId("ScienceAndEngineering") -> Red)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Red.toString),
          SchemeEvaluationResult(SchemeId("ScienceAndEngineering"), Red.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 80,SchemeId("European"), SchemeId("ScienceAndEngineering")) mustResultIn(
          PHASE2_TESTS_FAILED, SchemeId("European") -> Red, SchemeId("ScienceAndEngineering") -> Red)
      }
    }
    "give no results when no schemes are in green and at-least one scheme is in amber" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 20,SchemeId("Commercial")) mustResultIn(
          PHASE2_TESTS, SchemeId("Commercial") -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString),
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 20,SchemeId("Commercial"), SchemeId("DigitalAndTechnology")) mustResultIn(
          PHASE2_TESTS, SchemeId("Commercial") -> Amber, SchemeId("DigitalAndTechnology") -> Red)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Amber.toString),
          SchemeEvaluationResult(SchemeId("ScienceAndEngineering"), Amber.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-3", 80,SchemeId("European"), SchemeId("ScienceAndEngineering")) mustResultIn(
          PHASE2_TESTS, SchemeId("European") -> Amber, SchemeId("ScienceAndEngineering") -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-4", 50,SchemeId("European")) mustResultIn(
          PHASE2_TESTS, SchemeId("European") -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Amber.toString),
          SchemeEvaluationResult(SchemeId("ProjectDelivery"), Amber.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-5", 50,SchemeId("European"), SchemeId("ProjectDelivery")) mustResultIn(
          PHASE2_TESTS, SchemeId("European") -> Amber, SchemeId("ProjectDelivery") -> Amber)
      }
    }
    "give pass results on re-evaluation when all schemes are green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("DiplomaticServiceEconomics"), Green.toString),
            SchemeEvaluationResult(SchemeId("DiplomaticServiceEuropean"), Green.toString)),
          "phase1-version1-res", None)

        applicationEvaluation("application-1", 40,SchemeId("DiplomaticServiceEconomics"), SchemeId("DiplomaticServiceEuropean")) mustResultIn(
          PHASE2_TESTS, SchemeId("DiplomaticServiceEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

        applicationReEvaluationWithSettings(
          (SchemeId("DiplomaticServiceEconomics"), 40, 40),
          (SchemeId("DiplomaticServiceEuropean"), 40, 40)
        ) mustResultIn(PHASE2_TESTS_PASSED, SchemeId("DiplomaticServiceEconomics") -> Green, SchemeId("DiplomaticServiceEuropean") -> Green)
      }
    }
    "give pass results on re-evaluation when at-least one scheme is green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(SchemeId("HumanResources"), Red.toString),
          SchemeEvaluationResult(SchemeId("ProjectDelivery"), Green.toString)),
          "phase1-version1-res", None)

        applicationEvaluation("application-2", 50,SchemeId("HumanResources"), SchemeId("ProjectDelivery")) mustResultIn(
          PHASE2_TESTS, SchemeId("HumanResources") -> Red, SchemeId("ProjectDelivery") -> Amber)

        applicationReEvaluationWithSettings(
          (SchemeId("ProjectDelivery"), 50, 50)
        ) mustResultIn(PHASE2_TESTS_PASSED, SchemeId("HumanResources") -> Red, SchemeId("ProjectDelivery") -> Green)
      }
    }
  }

  trait TestFixture {

    // format: OFF
    val phase2PassMarkSettingsTable = Table[SchemeId, Double, Double](
      ("Scheme Name", "Etray Fail Threshold", "Etray Pass threshold"),
      (SchemeId("Commercial"), 20.0, 80.0),
      (SchemeId("DigitalAndTechnology"), 20.001, 20.001),
      (SchemeId("DiplomaticService"), 20.01, 20.02),
      (SchemeId("DiplomaticServiceEconomics"), 30.0, 70.0),
      (SchemeId("DiplomaticServiceEuropean"), 30.0, 70.0),
      (SchemeId("European"), 40.0, 70.0),
      (SchemeId("Finance"), 25.01, 25.02),
      (SchemeId("Generalist"), 30.0, 30.0),
      (SchemeId("GovernmentCommunicationService"), 30.0, 70.0),
      (SchemeId("GovernmentEconomicsService"), 30.0, 70.0),
      (SchemeId("GovernmentOperationalResearchService"), 30.0, 70.0),
      (SchemeId("GovernmentSocialResearchService"), 30.0, 70.0),
      (SchemeId("GovernmentStatisticalService"), 30.0, 70.0),
      (SchemeId("HousesOfParliament"), 30.0, 79.999),
      (SchemeId("HumanResources"), 30.0, 50.0),
      (SchemeId("ProjectDelivery"), 30.0, 70.0),
      (SchemeId("ScienceAndEngineering"), 69.00, 69.00)
    )
    // format: ON

    var phase2PassMarkSettings: Phase2PassMarkSettings = _

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation = _

    var passMarkEvaluation: PassmarkEvaluation = _

    var phase1PassMarkEvaluation: PassmarkEvaluation = _

    def applicationEvaluation(applicationId: String, etrayScore: Double, selectedSchemes: SchemeId*): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase2TestResults(applicationId, etrayScore,
        phase1PassMarkEvaluation)(selectedSchemes: _*)
      phase2TestEvaluationService.evaluate(applicationReadyForEvaluation, phase2PassMarkSettings).futureValue
      this
    }

    def mustResultIn(expApplicationStatus: ApplicationStatus.ApplicationStatus, expSchemeResults: (SchemeId, Result)*): TestFixture = {
      passMarkEvaluation = phase2EvaluationRepo.getPassMarkEvaluation(applicationReadyForEvaluation.applicationId).futureValue
      val applicationStatus = ApplicationStatus.withName(
        applicationRepository.findStatus(applicationReadyForEvaluation.applicationId).futureValue.status)

      val schemeResults = passMarkEvaluation.result.map {
        SchemeEvaluationResult.unapply(_).map {
          case (schemeType, resultStr) => schemeType -> Result(resultStr)
        }.get
      }
      phase2PassMarkSettings.version mustBe passMarkEvaluation.passmarkVersion
      applicationStatus mustBe expApplicationStatus
      schemeResults must contain theSameElementsAs expSchemeResults
      passMarkEvaluation.previousPhasePassMarkVersion mustBe Some(phase1PassMarkEvaluation.passmarkVersion)
      this
    }

    def applicationReEvaluationWithSettings(newSchemeSettings: (SchemeId, Double, Double)*): TestFixture = {
      val schemePassMarkSettings = phase2PassMarkSettingsTable.filterNot(schemeSetting =>
        newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
      phase2PassMarkSettings = createPhase2PassMarkSettings(schemePassMarkSettings).futureValue
      phase2TestEvaluationService.evaluate(applicationReadyForEvaluation, phase2PassMarkSettings).futureValue
      this
    }

    private def createPhase2PassMarkSettings(phase2PassMarkSettingsTable:
                                             TableFor3[SchemeId, Double, Double]): Future[Phase2PassMarkSettings] = {
      val schemeThresholds = phase2PassMarkSettingsTable.map {
        fields => Phase2PassMark(fields._1,
          Phase2PassMarkThresholds(PassMarkThreshold(fields._2, fields._3)))
      }.toList

      val phase2PassMarkSettings = Phase2PassMarkSettings(
        schemeThresholds,
        "version-1",
        DateTime.now,
        "user-1"
      )
      phase2PassMarkSettingRepo.create(phase2PassMarkSettings).flatMap { _ =>
        phase2PassMarkSettingRepo.getLatestVersion.map(_.get)
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
      createPhase2PassMarkSettings(phase2PassMarkSettingsTable).map(phase2PassMarkSettings = _)
    )).futureValue
  }

}
