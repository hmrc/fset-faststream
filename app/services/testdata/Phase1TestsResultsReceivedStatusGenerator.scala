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

package services.testdata

import common.FutureEx
import model.OnlineTestCommands.TestResult
import model.ProgressStatuses
import model.exchange.CubiksTestResultReady
import model.persisted.CubiksTest
import model.command.testdata.GeneratorConfig
import org.joda.time.{ DateTime, DateTimeZone }
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase1TestRepository
import services.onlinetesting.Phase1TestService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Phase1TestsResultsReceivedStatusGenerator extends Phase1TestsResultsReceivedStatusGenerator {
  override val previousStatusGenerator = Phase1TestsCompletedStatusGenerator
  override val otRepository = phase1TestRepository
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

    val now = DateTime.now().withZone(DateTimeZone.UTC)
    def getPhase1Test(cubiksUserId: Int) = CubiksTest(0, true, cubiksUserId, "", "", "", now, 0)
    def getTestResult(tscore: Option[Double]) = TestResult("completed", "norm", tscore.orElse(Some(10.0)), Some(20.0), Some(30.0), Some(40.0))


    def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
      for {
        candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
        _ <- FutureEx.traverseSerial(candidate.phase1TestGroup.get.tests) { test =>
          val id = test.cubiksUserId
          val result = CubiksTestResultReady(Some(id * 123), "Ready", Some(s"http://fakeurl.com/report$id"))
          otService.markAsReportReadyToDownload(id, result)
        }
        cubiksUserIds <- Future.successful(candidate.phase1TestGroup.get.tests.map(_.cubiksUserId))
        testResults <- Future.successful(cubiksUserIds.map { id => { (getTestResult(generatorConfig.tscore), getPhase1Test(id))}})
        _ <- insertTests(candidate.applicationId.get, testResults)
        _ <- otRepository.updateProgressStatus(candidate.applicationId.get, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED)
      } yield candidate
    }
  }
