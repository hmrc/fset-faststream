package services.onlinetesting.phase3

import config.{ LaunchpadGatewayConfig, Phase3TestsConfig }
import factories.UUIDFactory
import model.ApplicationStatus.{ apply => _, _ }
import model.EvaluationResults.{ Amber, _ }
import model.Exceptions.PassMarkEvaluationNotFound
import model.exchange.passmarksettings._
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationStatus, SchemeId }
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.prop._
import repositories.{ CollectionNames, CommonRepository }
import testkit.MongoRepositorySpec

class Phase3TestEvaluationSpec extends MongoRepositorySpec with CommonRepository
  with TableDrivenPropertyChecks {

  val collectionName: String = CollectionNames.APPLICATION
  override val additionalCollections = List(CollectionNames.PHASE3_PASS_MARK_SETTINGS)

  def phase3TestEvaluationService(verifyAllScoresArePresent: Boolean = true) = {

    val launchpadGWConfig = LaunchpadGatewayConfig(
      url = "",
      Phase3TestsConfig(
        timeToExpireInDays = 7, invigilatedTimeToExpireInDays = 7,  gracePeriodInSecs = 0, candidateCompletionRedirectUrl = "",
        interviewsByAdjustmentPercentage = Map.empty, evaluationWaitTimeAfterResultsReceivedInHours = 3, verifyAllScoresArePresent
      )
    )
    when(mockAppConfig.launchpadGatewayConfig).thenReturn(launchpadGWConfig)

    new EvaluatePhase3ResultService (
      phase3EvaluationRepo,
      phase3PassMarkSettingRepo,
      applicationRepository,
      mockAppConfig,
      UUIDFactory
    )
  }

  "phase3 evaluation process" should {
    "not save any information to the database if we require all scores to be present and one score is missing" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("Commercial"),
          Green.toString), SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString)), "phase2-version1-res", None)

        applicationEvaluation("application-1", None, true,SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber"))

        phase3EvaluationRepo.getPassMarkEvaluation("application-1").failed.futureValue mustBe a[PassMarkEvaluationNotFound]
      }
    }

    "give fail results when all schemes are red and one score is empty and we disable verification that checks " +
      "all scores are present" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("Commercial"), Red.toString),
          SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Red.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-1", None, false,SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")) mustResultIn(
          PHASE3_TESTS_FAILED, SchemeId("Commercial") -> Red, SchemeId("DigitalDataTechnologyAndCyber") -> Red)
      }
    }

    "give pass results when all schemes are green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("Commercial"),
          Green.toString), SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-1", Some(80), true,SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")) mustResultIn(
          PHASE3_TESTS_PASSED, SchemeId("Commercial") -> Green, SchemeId("DigitalDataTechnologyAndCyber") -> Green)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(SchemeId("HousesOfParliament"), Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-2", Some(79.999), true,SchemeId("HousesOfParliament")) mustResultIn(
          PHASE3_TESTS_PASSED, SchemeId("HousesOfParliament") -> Green)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(SchemeId("Generalist"), Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-3", Some(30), true,SchemeId("Generalist")) mustResultIn(
          PHASE3_TESTS_PASSED, SchemeId("Generalist") -> Green)
      }
    }

    "give pass results when there is no amber and at-least one scheme is green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("Commercial"), Red.toString),
          SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-1", Some(80), true,SchemeId("Commercial"), SchemeId("DigitalDataTechnologyAndCyber")) mustResultIn(
          PHASE3_TESTS_PASSED, SchemeId("Commercial") -> Red, SchemeId("DigitalDataTechnologyAndCyber") -> Green)
      }
    }

    "give fail results when all the schemes are red" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("European"), Green.toString),
          SchemeEvaluationResult(SchemeId("ScienceAndEngineering"), Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-1", Some(35), true,SchemeId("European"), SchemeId("ScienceAndEngineering")) mustResultIn(
          PHASE3_TESTS_FAILED, SchemeId("European") -> Red, SchemeId("ScienceAndEngineering") -> Red)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("European"), Red.toString),
          SchemeEvaluationResult(SchemeId("ScienceAndEngineering"), Red.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-2", Some(80), true,SchemeId("European"), SchemeId("ScienceAndEngineering")) mustResultIn(
          PHASE3_TESTS_FAILED, SchemeId("European") -> Red, SchemeId("ScienceAndEngineering") -> Red)
      }
    }

    "give no results when at-least one scheme is in amber" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-1", Some(40), true, SchemeId("European")) mustResultIn(
          PHASE3_TESTS, SchemeId("European") -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("European"), Amber.toString),
          SchemeEvaluationResult(SchemeId("ScienceAndEngineering"), Amber.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-2", Some(80), true, SchemeId("European"), SchemeId("ScienceAndEngineering")) mustResultIn(
          PHASE3_TESTS, SchemeId("European") -> Amber, SchemeId("ScienceAndEngineering") -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(SchemeId("European"), Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-3", Some(50), true, SchemeId("European")) mustResultIn(
          PHASE3_TESTS, SchemeId("European") -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("European"), Amber.toString),
          SchemeEvaluationResult(SchemeId("ProjectDelivery"), Amber.toString)), "phase2-version1-res", None)

        applicationEvaluation("application-4", Some(50), true, SchemeId("European"), SchemeId("ProjectDelivery")) mustResultIn(
          PHASE3_TESTS, SchemeId("European") -> Amber, SchemeId("ProjectDelivery") -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("HumanResources"),
          Green.toString), SchemeEvaluationResult(SchemeId("ProjectDelivery"), Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-5", Some(50), true, SchemeId("HumanResources"), SchemeId("ProjectDelivery")) mustResultIn(
          PHASE3_TESTS_PASSED_WITH_AMBER, SchemeId("HumanResources") -> Green, SchemeId("ProjectDelivery") -> Amber)
      }
    }

    "give pass results on re-evaluation when all schemes are green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(SchemeId("DiplomaticAndDevelopmentEconomics"), Green.toString),
            SchemeEvaluationResult(SchemeId("DiplomaticServiceEuropean"), Green.toString)),
          "phase2-version1-res", None)

        applicationEvaluation("application-1", Some(40), true,
          SchemeId("DiplomaticAndDevelopmentEconomics"), SchemeId("DiplomaticServiceEuropean"))
        mustResultIn(PHASE3_TESTS, SchemeId("DiplomaticAndDevelopmentEconomics") -> Amber, SchemeId("DiplomaticServiceEuropean") -> Amber)

        applicationReEvaluationWithSettings(
          (SchemeId("DiplomaticAndDevelopmentEconomics"), 40, 40),
          (SchemeId("DiplomaticServiceEuropean"), 40, 40)
        ) mustResultIn(PHASE3_TESTS_PASSED, SchemeId("DiplomaticAndDevelopmentEconomics") -> Green,
          SchemeId("DiplomaticServiceEuropean") -> Green)
      }
    }

    "give pass results on re-evaluation when at-least one scheme is green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("HumanResources"),
          Red.toString), SchemeEvaluationResult(SchemeId("ProjectDelivery"), Green.toString)), "phase2-version1-res", None)

        applicationEvaluation("application-2", Some(50), true,SchemeId("HumanResources"), SchemeId("ProjectDelivery")) mustResultIn(
          PHASE3_TESTS, SchemeId("HumanResources") -> Red, SchemeId("ProjectDelivery") -> Amber)

        applicationReEvaluationWithSettings(
          (SchemeId("ProjectDelivery"), 50, 50)
        ) mustResultIn(PHASE3_TESTS_PASSED, SchemeId("HumanResources") -> Red, SchemeId("ProjectDelivery") -> Green)
      }
    }

    "move candidate from PHASE3_TESTS_PASSED_WITH_AMBER to PHASE3_TESTS_PASSED " in new TestFixture {
      phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(SchemeId("HumanResources"),
        Green.toString), SchemeEvaluationResult(SchemeId("ProjectDelivery"), Green.toString)), "phase2-version1-res", None)
      applicationEvaluation("application-4", Some(50), true,SchemeId("HumanResources"), SchemeId("ProjectDelivery")) mustResultIn(
        PHASE3_TESTS_PASSED_WITH_AMBER, SchemeId("HumanResources") -> Green, SchemeId("ProjectDelivery") -> Amber)

      applicationReEvaluationWithSettings(
        (SchemeId("ProjectDelivery"), 50, 50)
      ) mustResultIn(PHASE3_TESTS_PASSED, SchemeId("HumanResources") -> Green, SchemeId("ProjectDelivery") -> Green)
    }
  }

  trait TestFixture {

    // format: OFF
    val phase3PassMarkSettingsTable = Table[SchemeId, Double, Double](
      ("Scheme Name", "Video Interview Fail Threshold", "Video Interview Pass threshold"),
      (SchemeId("Commercial"), 20.0, 80.0),
      (SchemeId("DigitalDataTechnologyAndCyber"), 20.001, 20.001),
      (SchemeId("DiplomaticService"), 20.01, 20.02),
      (SchemeId("DiplomaticAndDevelopmentEconomics"), 30.0, 70.0),
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

    var phase3PassMarkSettings: Phase3PassMarkSettings = createPhase3PassMarkSettings(phase3PassMarkSettingsTable)

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation = _

    var passMarkEvaluation: PassmarkEvaluation = _

    var phase2PassMarkEvaluation: PassmarkEvaluation = _

    def applicationEvaluation(applicationId: String, videoInterviewScore: Option[Double],
                              verifyAllScoresArePresent: Boolean, selectedSchemes: SchemeId*): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase3TestResults(applicationId, videoInterviewScore,
        phase2PassMarkEvaluation)(selectedSchemes: _*)
      phase3TestEvaluationService(verifyAllScoresArePresent).evaluate(applicationReadyForEvaluation, phase3PassMarkSettings).futureValue
      this
    }

    def mustResultIn(expApplicationStatus: ApplicationStatus.ApplicationStatus, expSchemeResults: (SchemeId, Result)*): TestFixture = {
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

    def applicationReEvaluationWithSettings(newSchemeSettings: (SchemeId, Double, Double)*): TestFixture = {
      val schemePassMarkSettings = phase3PassMarkSettingsTable.filterNot(schemeSetting =>
        newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
      phase3PassMarkSettings = createPhase3PassMarkSettings(schemePassMarkSettings)
      phase3TestEvaluationService(false).evaluate(applicationReadyForEvaluation, phase3PassMarkSettings).futureValue
      this
    }

    private def createPhase3PassMarkSettings(phase3PassMarkSettingsTable:
                                             TableFor3[SchemeId, Double, Double]): Phase3PassMarkSettings = {
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
