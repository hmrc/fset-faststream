/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services.onlinetesting.phase3

import config.{LaunchpadGatewayConfig, Phase3TestsConfig}
import factories.UUIDFactory
import model.ApplicationStatus.{apply => _, _}
import model.EvaluationResults._
import model.Exceptions.PassMarkEvaluationNotFound
import model.exchange.passmarksettings._
import model.persisted.{ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult}
import model.{ApplicationStatus, SchemeId, Schemes}
import org.mockito.Mockito.when
import org.scalatest.prop._
import repositories.{CollectionNames, CommonRepository}
import testkit.MongoRepositorySpec

import java.time.OffsetDateTime

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
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Commercial,
          Green.toString), SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString)), "phase2-version1-res", None)

        applicationEvaluation("application-1", None, true, Commercial, DigitalDataTechnologyAndCyber)

        phase3EvaluationRepo.getPassMarkEvaluation("application-1").failed.futureValue mustBe a[PassMarkEvaluationNotFound]
      }
    }

    "give fail results when all schemes are red and one score is empty and we disable verification that checks " +
      "all scores are present" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Commercial, Red.toString),
          SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Red.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-1", None, false, Commercial, DigitalDataTechnologyAndCyber) mustResultIn(
          PHASE3_TESTS_FAILED, Commercial -> Red, DigitalDataTechnologyAndCyber -> Red)
      }
    }

    "give pass results when all schemes are green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Commercial,
          Green.toString), SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-1", Some(80), true, Commercial, DigitalDataTechnologyAndCyber) mustResultIn(
          PHASE3_TESTS_PASSED, Commercial -> Green, DigitalDataTechnologyAndCyber -> Green)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(HousesOfParliament, Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-2", Some(79.999), true, HousesOfParliament) mustResultIn(
          PHASE3_TESTS_PASSED, HousesOfParliament -> Green)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(OperationalDelivery, Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-3", Some(30), true, OperationalDelivery) mustResultIn(
          PHASE3_TESTS_PASSED, OperationalDelivery -> Green)
      }
    }

    "give pass results when there is no amber and at-least one scheme is green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Commercial, Red.toString),
          SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-1", Some(80), true, Commercial, DigitalDataTechnologyAndCyber) mustResultIn(
          PHASE3_TESTS_PASSED, Commercial -> Red, DigitalDataTechnologyAndCyber -> Green)
      }
    }

    "give fail results when all the schemes are red" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Property, Green.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-1", Some(35), true, Property, ScienceAndEngineering) mustResultIn(
          PHASE3_TESTS_FAILED, Property -> Red, ScienceAndEngineering -> Red)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Property, Red.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Red.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-2", Some(80), true, Property, ScienceAndEngineering) mustResultIn(
          PHASE3_TESTS_FAILED, Property -> Red, ScienceAndEngineering -> Red)
      }
    }

    "give no results when at-least one scheme is in amber" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(Property, Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-1", Some(40), true, Property) mustResultIn(
          PHASE3_TESTS, Property -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Property, Amber.toString),
          SchemeEvaluationResult(ScienceAndEngineering, Amber.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-2", Some(80), true, Property, ScienceAndEngineering) mustResultIn(
          PHASE3_TESTS, Property -> Amber, ScienceAndEngineering -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(Property, Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-3", Some(50), true, Property) mustResultIn(
          PHASE3_TESTS, Property -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(Property, Amber.toString),
          SchemeEvaluationResult(ProjectDelivery, Amber.toString)), "phase2-version1-res", None)

        applicationEvaluation("application-4", Some(50), true, Property, ProjectDelivery) mustResultIn(
          PHASE3_TESTS, Property -> Amber, ProjectDelivery -> Amber)
      }
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(HumanResources,
          Green.toString), SchemeEvaluationResult(ProjectDelivery, Green.toString)), "phase2-version1-res", None)
        applicationEvaluation("application-5", Some(50), true, HumanResources, ProjectDelivery) mustResultIn(
          PHASE3_TESTS_PASSED_WITH_AMBER, HumanResources -> Green, ProjectDelivery -> Amber)
      }
    }

    "give pass results on re-evaluation when all schemes are green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None,
          List(SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, Green.toString),
            SchemeEvaluationResult(GovernmentPolicy, Green.toString)),
          "phase2-version1-res", None)

        applicationEvaluation("application-1", Some(40), true,
          DiplomaticAndDevelopmentEconomics, GovernmentPolicy)
        mustResultIn(PHASE3_TESTS, DiplomaticAndDevelopmentEconomics -> Amber, GovernmentPolicy -> Amber)

        applicationReEvaluationWithSettings(
          (DiplomaticAndDevelopmentEconomics, 40, 40),
          (GovernmentPolicy, 40, 40)
        ) mustResultIn(PHASE3_TESTS_PASSED, DiplomaticAndDevelopmentEconomics -> Green,
          GovernmentPolicy -> Green)
      }
    }

    "give pass results on re-evaluation when at-least one scheme is green" in new TestFixture {
      {
        phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(HumanResources,
          Red.toString), SchemeEvaluationResult(ProjectDelivery, Green.toString)), "phase2-version1-res", None)

        applicationEvaluation("application-2", Some(50), true, HumanResources, ProjectDelivery) mustResultIn(
          PHASE3_TESTS, HumanResources -> Red, ProjectDelivery -> Amber)

        applicationReEvaluationWithSettings(
          (ProjectDelivery, 50, 50)
        ) mustResultIn(PHASE3_TESTS_PASSED, HumanResources -> Red, ProjectDelivery -> Green)
      }
    }

    "move candidate from PHASE3_TESTS_PASSED_WITH_AMBER to PHASE3_TESTS_PASSED " in new TestFixture {
      phase2PassMarkEvaluation = PassmarkEvaluation("phase2-version1", None, List(SchemeEvaluationResult(HumanResources,
        Green.toString), SchemeEvaluationResult(ProjectDelivery, Green.toString)), "phase2-version1-res", None)
      applicationEvaluation("application-4", Some(50), true, HumanResources, ProjectDelivery) mustResultIn(
        PHASE3_TESTS_PASSED_WITH_AMBER, HumanResources -> Green, ProjectDelivery -> Amber)

      applicationReEvaluationWithSettings(
        (ProjectDelivery, 50, 50)
      ) mustResultIn(PHASE3_TESTS_PASSED, HumanResources -> Green, ProjectDelivery -> Green)
    }
  }

  trait TestFixture extends Schemes {

    // format: OFF
    val phase3PassMarkSettingsTable = Table[SchemeId, Double, Double](
      ("Scheme Name",                           "Video Interview Fail", "Video Interview Pass"),
      (Commercial,                                20.0,                   80.0),
      (DigitalDataTechnologyAndCyber,             20.001,                 20.001),
      (DiplomaticAndDevelopment,                  20.01,                  20.02),
      (DiplomaticAndDevelopmentEconomics,         30.0,                   70.0),
      (Finance,                                   25.01,                  25.02),
      (GovernmentCommunicationService,            30.0,                   70.0),
      (GovernmentEconomicsService,                30.0,                   70.0),
      (GovernmentOperationalResearchService,      30.0,                   70.0),
      (GovernmentPolicy,                          30.0,                   70.0),
      (GovernmentSocialResearchService,           30.0,                   70.0),
      (GovernmentStatisticalService,              30.0,                   70.0),
      (HousesOfParliament,                        30.0,                   79.999),
      (HumanResources,                            30.0,                   50.0),
      (OperationalDelivery,                       30.0,                   30.0),
      (ProjectDelivery,                           30.0,                   70.0),
      (Property,                                  40.0,                   70.0),
      (ScienceAndEngineering,                     69.00,                  69.00)
    )
    // format: ON

    var phase3PassMarkSettings: Phase3PassMarkSettingsPersistence = createPhase3PassMarkSettings(phase3PassMarkSettingsTable)

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
      val schemePassMarkSettings = phase3PassMarkSettingsTable.filter(schemeSetting =>
        !newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
      phase3PassMarkSettings = createPhase3PassMarkSettings(schemePassMarkSettings)
      phase3TestEvaluationService(false).evaluate(applicationReadyForEvaluation, phase3PassMarkSettings).futureValue
      this
    }

    private def createPhase3PassMarkSettings(phase3PassMarkSettingsTable:
                                             TableFor3[SchemeId, Double, Double]): Phase3PassMarkSettingsPersistence = {
      val schemeThresholds = phase3PassMarkSettingsTable.map {
        fields => Phase3PassMark(fields._1,
          Phase3PassMarkThresholds(PassMarkThreshold(fields._2, fields._3)))
      }.toList

      val phase3PassMarkSettings = Phase3PassMarkSettingsPersistence(
        schemeThresholds,
        "version-1",
        OffsetDateTime.now,
        "user-1"
      )
      phase3PassMarkSettingRepo.create(phase3PassMarkSettings).futureValue
      phase3PassMarkSettingRepo.getLatestVersion.futureValue.get
    }
  }
}
