package repositories

import config.Phase2TestsConfig
import model.{ ApplicationStatus, Phase }
import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults.{ Amber, _ }
import model.SchemeType._
import model.exchange.passmarksettings._
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult }
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop._
import play.api.test.Helpers
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import reactivemongo.json.collection.JSONCollection
import services.onlinetesting.EvaluatePhase3ResultService
import testkit.MongoRepositorySpec

import scala.concurrent.Await


class Phase3TestEvaluationSpec extends MongoRepositorySpec with CommonRepository with MockitoSugar
  with TableDrivenPropertyChecks {

  import ImplicitBSONHandlers._

  val collectionName = "application"

  override def withFixture(test: NoArgTest) = {
    Helpers.running(app) {
      val collection = mongo().collection[JSONCollection]("phase3-pass-mark-settings")
      Await.ready(collection.remove(BSONDocument.empty), timeout)
      super.withFixture(test)
    }
  }

  def phase3TestEvaluationService = new EvaluatePhase3ResultService {
    val evaluationRepository = phase3EvaluationRepo
    val gatewayConfig = mockGatewayConfig
    val passMarkSettingsRepo = phase3PassMarkSettingRepo
    val phase = Phase.PHASE3
    val phase3TestsConfigMock = mock[Phase2TestsConfig]
  }

  "phase3 evaluation process" should {

    "give pass results when all schemes are green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(DigitalAndTechnology, Green.toString)))
        applicationEvaluation("application-1", 80, Commercial, DigitalAndTechnology) mustResultIn(
          PHASE3_TESTS_PASSED, Commercial -> Green, DigitalAndTechnology -> Green)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(HousesOfParliament, Green.toString)))
        applicationEvaluation("application-2", 79.999, HousesOfParliament) mustResultIn(
          PHASE3_TESTS_PASSED, HousesOfParliament -> Green)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Generalist, Green.toString)))
        applicationEvaluation("application-3", 30, Generalist) mustResultIn(
          PHASE3_TESTS_PASSED, Generalist -> Green)
      }
    }
    "give pass results when there is no amber and at-least one scheme is green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Commercial, Red.toString),
          SchemeEvaluationResult(DigitalAndTechnology, Green.toString)))
        applicationEvaluation("application-1", 80, Commercial, DigitalAndTechnology) mustResultIn(
          PHASE3_TESTS_PASSED, Commercial -> Red, DigitalAndTechnology -> Green)
      }
    }
    "give fail results when all the schemes are red" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(European, Green.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Green.toString)))
        applicationEvaluation("application-1", 35, European, ScienceAndEngineering) mustResultIn(
          PHASE3_TESTS_FAILED, European -> Red, ScienceAndEngineering -> Red)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(European, Red.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Red.toString)))
        applicationEvaluation("application-2", 80, European, ScienceAndEngineering) mustResultIn(
          PHASE3_TESTS_FAILED, European -> Red, ScienceAndEngineering -> Red)
      }
    }
    "give no results when at-least one scheme is in amber" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(European, Amber.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Amber.toString)))
        applicationEvaluation("application-1", 80, European, ScienceAndEngineering) mustResultIn(
          PHASE3_TESTS, European -> Amber, ScienceAndEngineering -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(European, Green.toString)))
        applicationEvaluation("application-2", 50, European) mustResultIn(
          PHASE3_TESTS, European -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(European, Amber.toString),
          SchemeEvaluationResult(ProjectDelivery, Amber.toString)))
        applicationEvaluation("application-3", 50, European, ProjectDelivery) mustResultIn(
          PHASE3_TESTS, European -> Amber, ProjectDelivery -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(HumanResources, Green.toString),
          SchemeEvaluationResult(ProjectDelivery, Green.toString)))
        applicationEvaluation("application-4", 50, HumanResources, ProjectDelivery) mustResultIn(
          PHASE3_TESTS, HumanResources -> Green, ProjectDelivery -> Amber)
      }
    }
    "give pass results on re-evaluation when all schemes are green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(DiplomaticServiceEconomics, Green.toString),
            SchemeEvaluationResult(DiplomaticServiceEuropean, Green.toString)))

        applicationEvaluation("application-1", 40, DiplomaticServiceEconomics, DiplomaticServiceEuropean) mustResultIn(
          PHASE3_TESTS, DiplomaticServiceEconomics -> Amber, DiplomaticServiceEuropean -> Amber)

        applicationReEvaluationWithSettings(
          (DiplomaticServiceEconomics, 40, 40),
          (DiplomaticServiceEuropean, 40, 40)
        ) mustResultIn(PHASE3_TESTS_PASSED, DiplomaticServiceEconomics -> Green, DiplomaticServiceEuropean -> Green)
      }
    }
    "give pass results on re-evaluation when at-least one scheme is green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(HumanResources, Red.toString),
          SchemeEvaluationResult(ProjectDelivery, Green.toString)))

        applicationEvaluation("application-2", 50, HumanResources, ProjectDelivery) mustResultIn(
          PHASE3_TESTS, HumanResources -> Red, ProjectDelivery -> Amber)

        applicationReEvaluationWithSettings(
          (ProjectDelivery, 50, 50)
        ) mustResultIn(PHASE3_TESTS_PASSED, HumanResources -> Red, ProjectDelivery -> Green)
      }
    }
  }

  trait TestFixture {

    // format: OFF
    val phase3PassMarkSettingsTable = Table[SchemeType, Double, Double](
      ("Scheme Name", "Video Interview Fail Threshold", "Video Interview Pass threshold"),
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

    var phase3PassMarkSettings = createPhase3PassMarkSettings(phase3PassMarkSettingsTable)

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation = _

    var passMarkEvaluation: PassmarkEvaluation = _

    var phase2PassMarkEvaluation: PassmarkEvaluation = _

    def applicationEvaluation(applicationId: String, videoInterviewScore: Double, selectedSchemes: SchemeType*): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase3TestResults(applicationId, videoInterviewScore,
        phase2PassMarkEvaluation)(selectedSchemes: _*)
      phase3TestEvaluationService.evaluate(applicationReadyForEvaluation, phase3PassMarkSettings).futureValue
      this
    }

    def mustResultIn(expApplicationStatus: ApplicationStatus.ApplicationStatus, expSchemeResults: (SchemeType, Result)*): TestFixture = {
      passMarkEvaluation = phase3EvaluationRepo.getPassMarkEvaluation(applicationReadyForEvaluation.applicationId).futureValue
      val applicationStatus = ApplicationStatus.withName(
        applicationRepository.findStatus(applicationReadyForEvaluation.applicationId).futureValue.status)

      val schemeResults = passMarkEvaluation.result.map {
        SchemeEvaluationResult.unapply(_).map {
          case (schemeType, resultStr) => schemeType -> Result(resultStr)
        }.get
      }
      phase3PassMarkSettings.version mustBe passMarkEvaluation.passmarkVersion
      applicationStatus mustBe expApplicationStatus
      schemeResults must contain theSameElementsAs expSchemeResults
      passMarkEvaluation.previousPhasePassMarkVersion mustBe Some(phase2PassMarkEvaluation.passmarkVersion)
      this
    }

    def applicationReEvaluationWithSettings(newSchemeSettings: (SchemeType, Double, Double)*): TestFixture = {
      val schemePassMarkSettings = phase3PassMarkSettingsTable.filterNot(schemeSetting =>
        newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
      phase3PassMarkSettings = createPhase3PassMarkSettings(schemePassMarkSettings)
      phase3TestEvaluationService.evaluate(applicationReadyForEvaluation, phase3PassMarkSettings).futureValue
      this
    }

    private def createPhase3PassMarkSettings(phase3PassMarkSettingsTable:
                                             TableFor3[SchemeType, Double, Double]): Phase3PassMarkSettings = {
      val schemeThresholds = phase3PassMarkSettingsTable.map {
        fields => Phase3PassMark(fields._1,
          Phase3PassMarkThresholds(PassMarkThreshold(fields._2, fields._3)))
      }.toList

      val phase3PassMarkSettings = Phase3PassMarkSettings(
        schemeThresholds,
        "version-1",
        DateTime.now,
        "user-1"
      )
      phase3PassMarkSettingRepo.create(phase3PassMarkSettings).futureValue
      phase3PassMarkSettingRepo.getLatestVersion.futureValue.get
    }

  }

}
