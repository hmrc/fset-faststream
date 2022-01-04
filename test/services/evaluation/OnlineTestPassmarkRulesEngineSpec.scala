/*
 * Copyright 2022 HM Revenue & Customs
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

package services.evaluation

import model.EvaluationResults._
import model.OnlineTestCommands._
import model.Scheme
import org.joda.time.DateTime

/* TODO: in faststream
class OnlineTestPassmarkRulesEngineSpec extends UnitSpec {
  //scalastyle:off
  val PassmarkSettings = Settings(
    Scheme(Schemes.Business, SchemeThresholds(t(1.0, 99.0), t(5.0, 94.0), t(10.0, 90.0), t(30.0, 85.0), combination = None))
      :: Scheme(Schemes.Commercial, SchemeThresholds(t(15.0, 94.0), t(20.0, 90.0), t(25.0, 50.0), t(29.0, 80.0), combination = None))
      :: Scheme(Schemes.DigitalAndTechnology, SchemeThresholds(t(30.0, 80.0), t(30.0, 80.0), t(30.0, 80.0), t(29.0, 80.0), combination = None))
      :: Scheme(Schemes.Finance, SchemeThresholds(t(50.0, 55.0), t(53.0, 70.0), t(30.0, 45.0), t(20.0, 30.0), combination = None))
      :: Scheme(Schemes.ProjectDelivery, SchemeThresholds(t(10.0, 55.0), t(53.0, 70.0), t(30.0, 45.0), t(20.0, 30.0), combination = None))
      :: Nil,
    version = "testVersion",
    createDate = new DateTime(),
    createdByUser = "testUser",
    setting = "location1Scheme1"
  )
  //scalastyle:on

  val CombinedPassmarkSettings = PassmarkSettings.copy(schemes =
    PassmarkSettings.schemes.map { scheme =>
      scheme.copy(schemeThresholds = scheme.schemeThresholds.copy(combination = Some(t(51.0, 80.0))))
    })

  "Pass mark rules engine for candidate with only one scheme" should {
    val prefs = PreferencesFixture.preferences(Schemes.Business)
    val scoresWithPassmark = CandidateScoresWithPreferencesAndPassmarkSettings(PassmarkSettings, prefs, FullTestReport,
      ApplicationStatuses.OnlineTestCompleted)

    "evaluate the scheme to GREEN when candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.0),
        verbal = tScore(94.0),
        numerical = tScore(99.3),
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Green, None, None, None, None))
    }

    "evaluate the scheme to AMBER when candidate does not fail any tests but has not passed one of them" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = tScore(79.0),
        numerical = tScore(100.0),
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Amber, None, None, None, None))
    }

    "evaluate the scheme to RED when a candidate has failed one test" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = tScore(5.0),
        numerical = tScore(100.0),
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Red, None, None, None, None))
    }

    "evaluate the scheme to GREEN when a GIS candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = None,
        numerical = None,
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Green, None, None, None, None))
    }

    "evaluate the scheme to AMBER when a GIS candidate does not fail any tests but has not passed one of them" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(79.2),
        verbal = None,
        numerical = None,
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Amber, None, None, None, None))
    }

    "evaluate the scheme to RED when a GIS candidate has failed one test" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = None,
        numerical = None,
        situational = tScore(19.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Red, None, None, None, None))
    }
  }

  "Pass mark rules engine for candidates with all schemes" should {
    val prefs = PreferencesFixture.preferences(Schemes.Business, Some(Schemes.Commercial),
      Some(Schemes.DigitalAndTechnology), Some(Schemes.Finance), Some(true))

    val scoresWithPassmark = CandidateScoresWithPreferencesAndPassmarkSettings(PassmarkSettings, prefs, FullTestReport,
      ApplicationStatuses.OnlineTestCompleted)

    "evaluate all the scheme to GREEN when a candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = tScore(94.0),
        numerical = tScore(90.3),
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Green, Some(Green), Some(Green), Some(Green), Some(Green)))
    }

    "evaluate all the scheme to AMBER when a candidate does not achieve all pass marks and at least one fail mark" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(54.2),
        verbal = tScore(69.0),
        numerical = tScore(90.3),
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Amber, Some(Amber), Some(Amber), Some(Amber), Some(Amber)))
    }

    "evaluate all the scheme to RED when a candidate gets a fail mark for all schemes" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(1.0),
        verbal = tScore(5.0),
        numerical = tScore(10.0),
        situational = tScore(20.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Red, Some(Red), Some(Red), Some(Red), Some(Red)))
    }

    "evaluate all the scheme to GREEN and do not set Alternative Scheme when a candidate does not want alternative schemes" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = tScore(94.0),
        numerical = tScore(90.3),
        situational = tScore(100.0)
      )
      val prefs = PreferencesFixture.preferences(Schemes.Business, Some(Schemes.Commercial),
        Some(Schemes.DigitalAndTechnology), Some(Schemes.Finance), alternativeScheme = Some(false))

      val result = OnlineTestPassmarkRulesEngine.evaluate(
        scoresWithPassmark.copy(preferences = prefs, scores = candidateScores)
      )

      result must be(RuleCategoryResult(Green, Some(Green), Some(Green), Some(Green), None))
    }

    "evaluate all the scheme to GREEN when a GIS candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = None,
        numerical = None,
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Green, Some(Green), Some(Green), Some(Green), Some(Green)))
    }

    "evaluate all the schemes according to scores for different passmark per scheme" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = tScore(90.0),
        numerical = tScore(50.3),
        situational = tScore(30.0)
      )

      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Red, Some(Amber), Some(Amber), Some(Green), Some(Green)))
    }
  }

  "Pass mark rules engine for combination score" should {
    val prefs = PreferencesFixture.preferences(Schemes.ProjectDelivery)
    val scoresWithPassmark = CandidateScoresWithPreferencesAndPassmarkSettings(PassmarkSettings, prefs, FullTestReport,
      ApplicationStatuses.OnlineTestCompleted)
    val combinedScoresWithPassmark = CandidateScoresWithPreferencesAndPassmarkSettings(CombinedPassmarkSettings, prefs, FullTestReport,
      ApplicationStatuses.OnlineTestCompleted)

    "evaluate the schemes to AMBER even though individual scores have evaluated the scheme to GREEN" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(60.2),
        verbal = tScore(75.0),
        numerical = tScore(45.3),
        situational = tScore(30.0)
      )

      val resultForIndividualScores = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
      val resultForCombinationScore = OnlineTestPassmarkRulesEngine.evaluate(combinedScoresWithPassmark.copy(scores = candidateScores))

      resultForIndividualScores must be(RuleCategoryResult(Green, None, None, None, None))
      resultForCombinationScore must be(RuleCategoryResult(Amber, None, None, None, None))
    }

    "evaluate the schemes to RED even though individual scores have evaluated the scheme to GREEN" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(55.2),
        verbal = tScore(70.0),
        numerical = tScore(45.3),
        situational = tScore(30.0)
      )

      val resultForIndividualScores = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
      val resultForCombinationScore = OnlineTestPassmarkRulesEngine.evaluate(combinedScoresWithPassmark.copy(scores = candidateScores))

      resultForIndividualScores must be(RuleCategoryResult(Green, None, None, None, None))
      resultForCombinationScore must be(RuleCategoryResult(Red, None, None, None, None))
    }

    "evaluate the schemes to GREEN when individual score evaluated the scheme to GREEN" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(90.2),
        verbal = tScore(90.0),
        numerical = tScore(80.3),
        situational = tScore(70.0)
      )

      val resultForIndividualScores = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
      val resultForCombinationScore = OnlineTestPassmarkRulesEngine.evaluate(combinedScoresWithPassmark.copy(scores = candidateScores))

      resultForIndividualScores must be(RuleCategoryResult(Green, None, None, None, None))
      resultForCombinationScore must be(RuleCategoryResult(Green, None, None, None, None))
    }

    "evaluate the schemes to AMBER even though individual score for GIS candidate has evaluated the scheme to GREEN" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(90.2),
        verbal = None,
        numerical = None,
        situational = tScore(30.0)
      )

      val resultForIndividualScores = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
      val resultForCombinationScore = OnlineTestPassmarkRulesEngine.evaluate(combinedScoresWithPassmark.copy(scores = candidateScores))

      resultForIndividualScores must be(RuleCategoryResult(Green, None, None, None, None))
      resultForCombinationScore must be(RuleCategoryResult(Amber, None, None, None, None))
    }

    "evaluate the schemes to RED when individual score evaluated the scheme to AMBER" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(60.2),
        verbal = tScore(60.0),
        numerical = tScore(40.3),
        situational = tScore(25.0)
      )

      val resultForIndividualScores = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
      val resultForCombinationScore = OnlineTestPassmarkRulesEngine.evaluate(combinedScoresWithPassmark.copy(scores = candidateScores))

      resultForIndividualScores must be(RuleCategoryResult(Amber, None, None, None, None))
      resultForCombinationScore must be(RuleCategoryResult(Red, None, None, None, None))
    }

    "evaluate the schemes to RED when individual score evaluated the scheme to RED" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(100.0),
        verbal = tScore(100.0),
        numerical = tScore(100.0),
        situational = tScore(19.0)
      )

      val resultForIndividualScores = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
      val resultForCombinationScore = OnlineTestPassmarkRulesEngine.evaluate(combinedScoresWithPassmark.copy(scores = candidateScores))

      resultForIndividualScores must be(RuleCategoryResult(Red, None, None, None, None))
      resultForCombinationScore must be(RuleCategoryResult(Red, None, None, None, None))
    }
  }

  "Pass mark rules engine" should {
    "throw an exception when there is no passmark for the Scheme" in {
      val prefs = PreferencesFixture.preferences(Schemes.Business)
      val passmarkWithoutBusinessScheme = CandidateScoresWithPreferencesAndPassmarkSettings(
        PassmarkSettings.copy(
          schemes = PassmarkSettings.schemes.filterNot(_.schemeName == Schemes.Business)
        ), prefs, FullTestReport, ApplicationStatuses.OnlineTestCompleted
      )

      intercept[IllegalStateException] {
        OnlineTestPassmarkRulesEngine.evaluate(passmarkWithoutBusinessScheme)
      }
    }

    "throw an exception when the candidate's report does not have tScore" in {
      val prefs = PreferencesFixture.preferences(Schemes.Business)
      val candidateScores = CandidateScoresWithPreferencesAndPassmarkSettings(
        PassmarkSettings,
        prefs, FullTestReport.copy(competency = noTScore),
        ApplicationStatuses.OnlineTestCompleted
      )

      intercept[IllegalArgumentException] {
        OnlineTestPassmarkRulesEngine.evaluate(candidateScores)
      }
    }

  }

  private def tScore(score: Double): Option[TestResult] = Some(TestResult("", "", Some(score), None, None, None))
  private def noTScore: Option[TestResult] = Some(TestResult("", "", None, None, None, None))
  private def t(failThreshold: Double, passThreshold: Double) = SchemeThreshold(failThreshold, passThreshold)
}
*/
