package repositories

import config.{ LaunchpadGatewayConfig, Phase2TestsConfig, Phase3TestsConfig }
import model.{ ApplicationStatus, Phase }
import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults.{ Amber, _ }
import model.SchemeType._
import model.exchange.passmarksettings._
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult }
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop._
import reactivemongo.json.ImplicitBSONHandlers
import services.onlinetesting.phase3.EvaluatePhase3ResultService
import testkit.MongoRepositorySpec


class Phase3TestEvaluationSpec extends MongoRepositorySpec with CommonRepository
  with TableDrivenPropertyChecks {

  import ImplicitBSONHandlers._

  val collectionName = "application"
  override val additionalCollections = List("phase3-pass-mark-settings")

  def phase3TestEvaluationService(verifyAllScoresArePresent: Boolean = true) = new EvaluatePhase3ResultService {
    val evaluationRepository = phase3EvaluationRepo
    val gatewayConfig = mockGatewayConfig
    val passMarkSettingsRepo = phase3PassMarkSettingRepo
    val phase = Phase.PHASE3
    val phase3TestsConfigMock = mock[Phase2TestsConfig]
    val launchpadGWConfig = LaunchpadGatewayConfig(url = "", phase3Tests = Phase3TestsConfig(7, 7, "", Map.empty, 3, verifyAllScoresArePresent))
  }

  "phase3 evaluation process" should {
    "throw IllegalArgumentException if we require all score to be present and one score is missing" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(DigitalAndTechnology, Green.toString)))
        intercept[IllegalArgumentException] {
          applicationEvaluation("application-1", None, true, Commercial, DigitalAndTechnology) mustResultIn(
            PHASE3_TESTS_PASSED, Commercial -> Green, DigitalAndTechnology -> Green)
        }
      }
    }
    "give fail results when all schemes are red and one score is empty and we disable verifcation that checks all scores are present" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Commercial, Red.toString),
          SchemeEvaluationResult(DigitalAndTechnology, Red.toString)))
        applicationEvaluation("application-1", None, false, Commercial, DigitalAndTechnology) mustResultIn(
          PHASE3_TESTS_FAILED, Commercial -> Red, DigitalAndTechnology -> Red)
      }
    }
    "give pass results when all schemes are green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(DigitalAndTechnology, Green.toString)))
        applicationEvaluation("application-1", Some(80), true, Commercial, DigitalAndTechnology) mustResultIn(
          PHASE3_TESTS_PASSED, Commercial -> Green, DigitalAndTechnology -> Green)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(HousesOfParliament, Green.toString)))
        applicationEvaluation("application-2", Some(79.999), true, HousesOfParliament) mustResultIn(
          PHASE3_TESTS_PASSED, HousesOfParliament -> Green)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Generalist, Green.toString)))
        applicationEvaluation("application-3", Some(30), true, Generalist) mustResultIn(
          PHASE3_TESTS_PASSED, Generalist -> Green)
      }
    }

    "give pass results when there is no amber and at-least one scheme is green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Commercial, Red.toString),
          SchemeEvaluationResult(DigitalAndTechnology, Green.toString)))
        applicationEvaluation("application-1", Some(80), true, Commercial, DigitalAndTechnology) mustResultIn(
          PHASE3_TESTS_PASSED, Commercial -> Red, DigitalAndTechnology -> Green)
      }
    }
    "give fail results when all the schemes are red" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(European, Green.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Green.toString)))
        applicationEvaluation("application-1", Some(35), true, European, ScienceAndEngineering) mustResultIn(
          PHASE3_TESTS_FAILED, European -> Red, ScienceAndEngineering -> Red)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(European, Red.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Red.toString)))
        applicationEvaluation("application-2", Some(80), true, European, ScienceAndEngineering) mustResultIn(
          PHASE3_TESTS_FAILED, European -> Red, ScienceAndEngineering -> Red)
      }
    }
    "give no results when at-least one scheme is in amber" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(European, Amber.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Amber.toString)))
        applicationEvaluation("application-1", Some(80), true, European, ScienceAndEngineering) mustResultIn(
          PHASE3_TESTS, European -> Amber, ScienceAndEngineering -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(European, Green.toString)))
        applicationEvaluation("application-2", Some(50), true, European) mustResultIn(
          PHASE3_TESTS, European -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(European, Amber.toString),
          SchemeEvaluationResult(ProjectDelivery, Amber.toString)))
        applicationEvaluation("application-3", Some(50), true, European, ProjectDelivery) mustResultIn(
          PHASE3_TESTS, European -> Amber, ProjectDelivery -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(HumanResources, Green.toString),
          SchemeEvaluationResult(ProjectDelivery, Green.toString)))
        applicationEvaluation("application-4", Some(50), true, HumanResources, ProjectDelivery) mustResultIn(
          PHASE3_TESTS_PASSED_WITH_AMBER, HumanResources -> Green, ProjectDelivery -> Amber)
      }
    }
    "give pass results on re-evaluation when all schemes are green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(DiplomaticServiceEconomics, Green.toString),
            SchemeEvaluationResult(DiplomaticServiceEuropean, Green.toString)))

        applicationEvaluation("application-1", Some(40), true, DiplomaticServiceEconomics, DiplomaticServiceEuropean) mustResultIn(
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

        applicationEvaluation("application-2", Some(50), true, HumanResources, ProjectDelivery) mustResultIn(
          PHASE3_TESTS, HumanResources -> Red, ProjectDelivery -> Amber)

        applicationReEvaluationWithSettings(
          (ProjectDelivery, 50, 50)
        ) mustResultIn(PHASE3_TESTS_PASSED, HumanResources -> Red, ProjectDelivery -> Green)
      }
    }
    "move candidate from PHASE3_TESTS_PASSED_WITH_AMBER to PHASE3_TESTS_PASSED " in new TestFixture {
      phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(HumanResources, Green.toString),
        SchemeEvaluationResult(ProjectDelivery, Green.toString)))
      applicationEvaluation("application-4", Some(50), true, HumanResources, ProjectDelivery) mustResultIn(
        PHASE3_TESTS_PASSED_WITH_AMBER, HumanResources -> Green, ProjectDelivery -> Amber)

      applicationReEvaluationWithSettings(
        (ProjectDelivery, 50, 50)
      ) mustResultIn(PHASE3_TESTS_PASSED, HumanResources -> Green, ProjectDelivery -> Green)
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

    def applicationEvaluation(applicationId: String, videoInterviewScore: Option[Double],
                              verifyAllScoresArePresent: Boolean, selectedSchemes: SchemeType*): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase3TestResults(applicationId, videoInterviewScore,
        phase2PassMarkEvaluation)(selectedSchemes: _*)
      phase3TestEvaluationService(verifyAllScoresArePresent).evaluate(applicationReadyForEvaluation, phase3PassMarkSettings).futureValue
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
      phase3TestEvaluationService(false).evaluate(applicationReadyForEvaluation, phase3PassMarkSettings).futureValue
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
