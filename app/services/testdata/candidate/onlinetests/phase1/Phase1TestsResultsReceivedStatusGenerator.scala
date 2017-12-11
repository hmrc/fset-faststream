/*
 * Copyright 2017 HM Revenue & Customs
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

package services.testdata.candidate.onlinetests.phase1

import common.FutureEx
import model.OnlineTestCommands.TestResult
import model.ProgressStatuses
import model.exchange.CubiksTestResultReady
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.persisted.CubiksTest
import model.testdata.CreateCandidateData.CreateCandidateData
import org.joda.time.{ DateTime, DateTimeZone }
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.{ Phase1TestMongoRepository, Phase1TestRepository }
import services.onlinetesting.phase1.Phase1TestService
import services.testdata.candidate.ConstructiveGenerator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object Phase1TestsResultsReceivedStatusGenerator extends Phase1TestsResultsReceivedStatusGenerator {
  override val previousStatusGenerator = Phase1TestsCompletedStatusGenerator
  override val otRepository: Phase1TestMongoRepository = phase1TestRepository
  override val otService = Phase1TestService
}

trait Phase1TestsResultsReceivedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase1TestRepository
  val otService: Phase1TestService


    def insertTests(applicationId: String, testResults: List[(TestResult, CubiksTest)]): Future[Unit] = {
      Future.sequence(testResults.map {
        case (result, phase1Test) => otRepository.insertTestResult(applicationId,
          phase1Test, model.persisted.TestResult.fromCommandObject(result)
        )
      }).map(_ => ())
    }

    val now: DateTime = DateTime.now().withZone(DateTimeZone.UTC)
    def getPhase1Test(cubiksUserId: Int) = CubiksTest(0, usedForResults = true, cubiksUserId, "", "", "", now, 0)
    def getTestResult(tscore: Option[Double]) = TestResult("completed", "norm", tscore.orElse(
      Some(tscore.getOrElse(10.0))), Some(tscore.getOrElse(20.0)), Some(tscore.getOrElse(30.0)), Some(tscore.getOrElse(40.0)))
    def getTest(candidate: CreateCandidateResponse, testType: String): Int =
      candidate.phase1TestGroup.get.tests.filter(_.testType == testType).head.testId


    def buildTestResults(candidate: CreateCandidateResponse, generatorConfig: CreateCandidateData): List[(TestResult, CubiksTest)] = {
      val sjqTestUserId = getTest(candidate, "sjq")

      if (generatorConfig.assistanceDetails.setGis) {
        getTestResult(generatorConfig.phase1TestData.flatMap(_.sjqtscore)) -> getPhase1Test(sjqTestUserId) :: Nil
      } else {
        val bqTestUserId = getTest(candidate, "bq")
        getTestResult(generatorConfig.phase1TestData.flatMap(_.bqtscore)) -> getPhase1Test(bqTestUserId) ::
        getTestResult(generatorConfig.phase1TestData.flatMap(_.sjqtscore)) -> getPhase1Test(sjqTestUserId) :: Nil
      }
    }

    def generate(generationId: Int, generatorConfig: CreateCandidateData)
      (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {
      for {
        candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
        _ <- FutureEx.traverseSerial(candidate.phase1TestGroup.get.tests) { test =>
          val id = test.testId
          val result = CubiksTestResultReady(Some(id * 123), "Ready", Some(s"http://fakeurl.com/report$id"))
          otService.markAsReportReadyToDownload(id, result)
        }
        cubiksUserIds = candidate.phase1TestGroup.get.tests.map(_.testId)
        tests = buildTestResults(candidate, generatorConfig)
        _ <- insertTests(candidate.applicationId.get, tests)
        _ <- otRepository.updateProgressStatus(candidate.applicationId.get, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED)
      } yield candidate
    }
  }
