package repositories

import config.Phase2TestsConfig
import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults.{ Amber, _ }
import model.SchemeType._
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
          List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(DigitalAndTechnology, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 80, Commercial, DigitalAndTechnology) mustResultIn(
          PHASE2_TESTS_PASSED, Commercial -> Green, DigitalAndTechnology -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(HousesOfParliament, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 79.999, HousesOfParliament) mustResultIn(
          PHASE2_TESTS_PASSED, HousesOfParliament -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Generalist, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-3", 30, Generalist) mustResultIn(
          PHASE2_TESTS_PASSED, Generalist -> Green)
      }
    }
    "give pass results when at-least one scheme is green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Commercial, Red.toString),
          SchemeEvaluationResult(DigitalAndTechnology, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 80, Commercial, DigitalAndTechnology) mustResultIn(
          PHASE2_TESTS_PASSED, Commercial -> Red, DigitalAndTechnology -> Green)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(HumanResources, Green.toString),
          SchemeEvaluationResult(ProjectDelivery, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 50, HumanResources, ProjectDelivery) mustResultIn(
          PHASE2_TESTS_PASSED, HumanResources -> Green, ProjectDelivery -> Amber)
      }
    }
    "give fail results when all the schemes are red" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(European, Green.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 35, European, ScienceAndEngineering) mustResultIn(
          PHASE2_TESTS_FAILED, European -> Red, ScienceAndEngineering -> Red)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(European, Red.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Red.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 80, European, ScienceAndEngineering) mustResultIn(
          PHASE2_TESTS_FAILED, European -> Red, ScienceAndEngineering -> Red)
      }
    }
    "give no results when no schemes are in green and at-least one scheme is in amber" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Commercial, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-1", 20, Commercial) mustResultIn(
          PHASE2_TESTS, Commercial -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(DigitalAndTechnology, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-2", 20, Commercial, DigitalAndTechnology) mustResultIn(
          PHASE2_TESTS, Commercial -> Amber, DigitalAndTechnology -> Red)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(European, Amber.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Amber.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-3", 80, European, ScienceAndEngineering) mustResultIn(
          PHASE2_TESTS, European -> Amber, ScienceAndEngineering -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(European, Green.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-4", 50, European) mustResultIn(
          PHASE2_TESTS, European -> Amber)
      }
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(European, Amber.toString),
          SchemeEvaluationResult(ProjectDelivery, Amber.toString)),
          "phase1-version1-res", None)
        applicationEvaluation("application-5", 50, European, ProjectDelivery) mustResultIn(
          PHASE2_TESTS, European -> Amber, ProjectDelivery -> Amber)
      }
    }
    "give pass results on re-evaluation when all schemes are green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(DiplomaticServiceEconomics, Green.toString),
            SchemeEvaluationResult(DiplomaticServiceEuropean, Green.toString)),
          "phase1-version1-res", None)

        applicationEvaluation("application-1", 40, DiplomaticServiceEconomics, DiplomaticServiceEuropean) mustResultIn(
          PHASE2_TESTS, DiplomaticServiceEconomics -> Amber, DiplomaticServiceEuropean -> Amber)

        applicationReEvaluationWithSettings(
          (DiplomaticServiceEconomics, 40, 40),
          (DiplomaticServiceEuropean, 40, 40)
        ) mustResultIn(PHASE2_TESTS_PASSED, DiplomaticServiceEconomics -> Green, DiplomaticServiceEuropean -> Green)
      }
    }
    "give pass results on re-evaluation when at-least one scheme is green" in new TestFixture {
      {
        phase1PassMarkEvaluation = PassmarkEvaluation("phase1-version1", None,
          List(SchemeEvaluationResult(HumanResources, Red.toString),
          SchemeEvaluationResult(ProjectDelivery, Green.toString)),
          "phase1-version1-res", None)

        applicationEvaluation("application-2", 50, HumanResources, ProjectDelivery) mustResultIn(
          PHASE2_TESTS, HumanResources -> Red, ProjectDelivery -> Amber)

        applicationReEvaluationWithSettings(
          (ProjectDelivery, 50, 50)
        ) mustResultIn(PHASE2_TESTS_PASSED, HumanResources -> Red, ProjectDelivery -> Green)
      }
    }
  }

  trait TestFixture {

    // format: OFF
    val phase2PassMarkSettingsTable = Table[SchemeType, Double, Double](
      ("Scheme Name", "Etray Fail Threshold", "Etray Pass threshold"),
      (Commercial, 20.0, 80.0),
      (DigitalAndTechnology, 20.001, 20.001),
      (DiplomaticService, 20.01, 20.02),
      (DiplomaticServiceEconomics, 30.0, 70.0),
      (DiplomaticServiceEuropean, 30.0, 70.0),
      (European, 40.0, 70.0),
      (Finance, 25.01, 25.02),
      (Generalist, 30.0, 30.0),
      (GovernmentCommunicationService, 30.0, 70.0),
      (GovernmentEconomicsService, 30.0, 70.0),
      (GovernmentOperationalResearchService, 30.0, 70.0),
      (GovernmentSocialResearchService, 30.0, 70.0),
      (GovernmentStatisticalService, 30.0, 70.0),
      (HousesOfParliament, 30.0, 79.999),
      (HumanResources, 30.0, 50.0),
      (ProjectDelivery, 30.0, 70.0),
      (ScienceAndEngineering, 69.00, 69.00)
    )
    // format: ON

    var phase2PassMarkSettings: Phase2PassMarkSettings = _

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation = _

    var passMarkEvaluation: PassmarkEvaluation = _

    var phase1PassMarkEvaluation: PassmarkEvaluation = _

    def applicationEvaluation(applicationId: String, etrayScore: Double, selectedSchemes: SchemeType*): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase2TestResults(applicationId, etrayScore,
        phase1PassMarkEvaluation)(selectedSchemes: _*)
      phase2TestEvaluationService.evaluate(applicationReadyForEvaluation, phase2PassMarkSettings).futureValue
      this
    }

    def mustResultIn(expApplicationStatus: ApplicationStatus.ApplicationStatus, expSchemeResults: (SchemeType, Result)*): TestFixture = {
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

    def applicationReEvaluationWithSettings(newSchemeSettings: (SchemeType, Double, Double)*): TestFixture = {
      val schemePassMarkSettings = phase2PassMarkSettingsTable.filterNot(schemeSetting =>
        newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
      phase2PassMarkSettings = createPhase2PassMarkSettings(schemePassMarkSettings).futureValue
      phase2TestEvaluationService.evaluate(applicationReadyForEvaluation, phase2PassMarkSettings).futureValue
      this
    }

    private def createPhase2PassMarkSettings(phase2PassMarkSettingsTable:
                                             TableFor3[SchemeType, Double, Double]): Future[Phase2PassMarkSettings] = {
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
