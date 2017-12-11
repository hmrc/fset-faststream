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

package services.testdata.candidate.onlinetests.phase2

import common.FutureEx
import model.OnlineTestCommands.TestResult
import model.ProgressStatuses
import model.exchange.CubiksTestResultReady
import model.persisted.CubiksTest
import model.testdata.CreateCandidateData.CreateCandidateData
import org.joda.time.{ DateTime, DateTimeZone }
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase2TestRepository
import services.onlinetesting.phase2.Phase2TestService
import services.testdata.candidate.ConstructiveGenerator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object Phase2TestsResultsReceivedStatusGenerator extends Phase2TestsResultsReceivedStatusGenerator {
  val previousStatusGenerator = Phase2TestsCompletedStatusGenerator
  val otRepository = phase2TestRepository
  val otService = Phase2TestService
}

trait Phase2TestsResultsReceivedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase2TestRepository
  val otService: Phase2TestService


    def insertTests(applicationId: String, testResults: List[(TestResult, CubiksTest)]): Future[Unit] = {
      Future.sequence(testResults.map {
        case (result, phase2Test) => otRepository.insertTestResult(applicationId,
          phase2Test, model.persisted.TestResult.fromCommandObject(result)
        )
      }).map(_ => ())
    }

    val now = DateTime.now().withZone(DateTimeZone.UTC)
    def getPhase2Test(cubiksUserId: Int) = CubiksTest(0, usedForResults = true, cubiksUserId, "", "", "", now, 0)
    def getTestResult(tscore: Option[Double]) = TestResult("completed", "norm", tscore.orElse(Some(10.0)), Some(20.0), Some(30.0), Some(40.0))


    def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
      for {
        candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
        _ <- FutureEx.traverseSerial(candidate.phase2TestGroup.get.tests) { test =>
          val id = test.testId
          val result = CubiksTestResultReady(Some(id * 123), "Ready", Some(s"http://fakeurl.com/report$id"))
          otService.markAsReportReadyToDownload(id, result)
        }
        cubiksUserIds <- Future.successful(candidate.phase2TestGroup.get.tests.map(_.testId))
        testResults <- Future.successful(cubiksUserIds.map { id => {
          (getTestResult(generatorConfig.phase2TestData.flatMap(_.tscore)), getPhase2Test(id))}
        })
        _ <- insertTests(candidate.applicationId.get, testResults)
        _ <- otRepository.updateProgressStatus(candidate.applicationId.get, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED)
      } yield candidate
    }
  }
