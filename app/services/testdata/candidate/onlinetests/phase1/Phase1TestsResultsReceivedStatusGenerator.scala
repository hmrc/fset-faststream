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

package services.testdata.candidate.onlinetests.phase1

import common.FutureEx
import javax.inject.{ Inject, Singleton }
import model.exchange.PsiRealTimeResults
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import model.testdata.candidate.Phase1TestData
import play.api.mvc.RequestHeader
import services.onlinetesting.phase1.Phase1TestService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class Phase1TestsResultsReceivedStatusGenerator @Inject() (val previousStatusGenerator: Phase1TestsCompletedStatusGenerator,
                                                           otService: Phase1TestService) extends ConstructiveGenerator {

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      scores <- Future.successful(generatorConfig.phase1TestData.getOrElse(
        Phase1TestData(scores = List(10.0, 20.0, 30.0, 40.0))).scores)
      _ <- FutureEx.traverseSerial(candidate.phase1TestGroup.get.tests zip scores){ case (test, score) =>
        otService.storeRealTimeResults(test.orderId, PsiRealTimeResults(score, score, Some("http://localhost/testReport")))}
    } yield candidate
  }
}
