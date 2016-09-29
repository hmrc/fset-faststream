/*
 * Copyright 2016 HM Revenue & Customs
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

package services

import mocks.PassMarkSettingsInMemoryRepository
import model.PersistedObjects.CandidateTestReport
import model.Preferences
import org.scalatest.mock.MockitoSugar
import repositories.onlinetesting.OnlineTestRepository
import repositories.{ FrameworkPreferenceRepository, FrameworkRepository, PassMarkSettingsRepository, TestReportRepository }
import services.onlinetesting.OnlineTestPassmarkService
import services.passmarksettings.PassMarkSettingsService
import testkit.IntegrationSpec

case class OnlineTestPassmarkServiceTest(preferences: Preferences,
                                         scores: CandidateTestReport,
                                         expected: ScoreEvaluationTestExpectation,
                                         previousEvaluation: Option[ScoreEvaluationTestExpectation] = None)

class OnlineTestPassmarkServiceSpec extends IntegrationSpec with MockitoSugar {

  lazy val service = new OnlineTestPassmarkService {

    val fpRepository = mock[FrameworkPreferenceRepository]
    val trRepository = mock[TestReportRepository]
    val oRepository = mock[OnlineTestRepository]
    val passmarkRulesEngine = OnlineTestPassmarkService.passmarkRulesEngine
    val pmsRepository: PassMarkSettingsRepository = PassMarkSettingsInMemoryRepository
    val passMarkSettingsService = new PassMarkSettingsService {
      override val fwRepository = mock[FrameworkRepository]
      override val pmsRepository = PassMarkSettingsInMemoryRepository
    }
  }

  /* TODO: Refactor in faststream
  "Online Test Passmark Service" should {
    "for each test in the path evaluate scores" in new WithApplication {
      implicit object DateTimeValueReader extends ValueReader[DateTime] {
        override def read(config: Config, path: String): DateTime = {
          DateTime.parse(config.getString(path))
        }
      }
      val suites = new File("it/resources/onlineTestPassmarkServiceSpec").listFiles.filterNot(_.getName.startsWith("."))
      // Test should fail in case the path is incorrect for the env and return 0 suites
      suites.nonEmpty must be(true)

      suites.foreach { suiteName =>
        val suiteFileName = suiteName.getName

        val passmarkSettingsFile = new File(suiteName.getAbsolutePath + "/passmarkSettings.conf")
        require(passmarkSettingsFile.exists(), s"File does not exist: ${passmarkSettingsFile.getAbsolutePath}")
        val passmarkSettings = ConfigFactory.parseFile(passmarkSettingsFile)
          .as[Settings]("passmarkSettings")
        val testCases = new File(s"it/resources/onlineTestPassmarkServiceSpec/$suiteFileName/").listFiles

        // Test should fail in case the path is incorrect for the env and return 0 tests
        testCases.nonEmpty must be(true)

        testCases.filterNot(t => t.getName == "passmarkSettings.conf").foreach { testCase =>
          val testCaseFileName = testCase.getName
          val tests: List[OnlineTestPassmarkServiceTest] = ConfigFactory.parseFile(
            new File(testCase.getAbsolutePath)).as[List[OnlineTestPassmarkServiceTest]]("tests")
          tests.foreach { t =>
            val expected = t.expected
            val alreadyEvaluated = t.previousEvaluation

            val candidateScoreWithPassmark = CandidateScoresWithPreferencesAndPassmarkSettings(passmarkSettings,
              t.preferences, t.scores, alreadyEvaluated.map(_.applicationStatus).getOrElse(ApplicationStatuses.OnlineTestCompleted))

            val actual = service.oRepository.inMemoryRepo
            val appId = t.scores.applicationId

            evaluate(appId, candidateScoreWithPassmark, alreadyEvaluated)

            val actualResult = actual(appId)

            val testMessage = s"suite=$suiteFileName, test=$testCaseFileName, applicationId=$appId"
            Logger.info(s"Processing $testMessage")

            withClue(testMessage + " location1Scheme1") {
              toStr(actualResult.result.location1Scheme1) must be(expected.location1Scheme1)
            }
            withClue(testMessage + " location1Scheme2") {
              toStr(actualResult.result.location1Scheme2) must be(expected.location1Scheme2)
            }
            withClue(testMessage + " location2Scheme1") {
              toStr(actualResult.result.location2Scheme1) must be(expected.location2Scheme1)
            }
            withClue(testMessage + " location2Scheme2") {
              toStr(actualResult.result.location2Scheme2) must be(expected.location2Scheme2)
            }
            withClue(testMessage + " alternativeScheme") {
              toStr(actualResult.result.alternativeScheme) must be(expected.alternativeScheme)
            }
            withClue(testMessage + " applicationStatus") {
              actualResult.applicationStatus must be(expected.applicationStatus)
            }
            withClue(testMessage + " version") {
              actualResult.version must be(passmarkSettings.version)
            }
          }
        }
      }
    }
  }

  def evaluate(appId: String, candidateScoreWithPassmark: CandidateScoresWithPreferencesAndPassmarkSettings,
               alreadyEvaluated: Option[ScoreEvaluationTestExpectation] = None) = {
    candidateScoreWithPassmark.applicationStatus match {
      case ApplicationStatuses.AssessmentScoresAccepted =>
        require(alreadyEvaluated.isDefined, "AssessmentScoresAccepted requires already evaluated application")

        alreadyEvaluated.map { currentEvaluation =>
          val currentResult = RuleCategoryResult(
            Result(currentEvaluation.location1Scheme1),
            currentEvaluation.location1Scheme2.map(Result(_)),
            currentEvaluation.location2Scheme1.map(Result(_)),
            currentEvaluation.location2Scheme2.map(Result(_)),
            currentEvaluation.alternativeScheme.map(Result(_))
          )

          service.oRepository.savePassMarkScore(appId, currentEvaluation.passmarkVersion.get,
            currentResult, currentEvaluation.applicationStatus)
        }

        service.evaluateCandidateScoreWithoutChangingApplicationStatus(candidateScoreWithPassmark)
      case ApplicationStatuses.OnlineTestCompleted =>
        service.evaluateCandidateScore(candidateScoreWithPassmark)
      case _ =>
        throw new IllegalArgumentException("This status is not supported by the test framework")
    }
  }

  def toStr(r: Result): String = r.getClass.getSimpleName.split("\\$").head
  def toStr(r: Option[Result]): Option[String] = r.map(toStr)
  */
}
