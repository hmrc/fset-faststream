/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.exchange.PsiRealTimeResults
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import model.testdata.candidate.Phase2TestData
import play.api.mvc.RequestHeader
import services.onlinetesting.phase2.Phase2TestService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Phase2TestsResultsReceivedStatusGenerator @Inject() (val previousStatusGenerator: Phase2TestsCompletedStatusGenerator,
                                                           otService: Phase2TestService
                                                          )(implicit ec: ExecutionContext) extends ConstructiveGenerator {

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext) = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      scores <- Future.successful(generatorConfig.phase2TestData.getOrElse(
        Phase2TestData(scores = List(11.0, 22.0))).scores)
      _ <- FutureEx.traverseSerial(candidate.phase2TestGroup.get.tests zip scores) { case (test, score) =>
        otService.storeRealTimeResults(test.orderId, PsiRealTimeResults(score, score, Some("http://localhost/testReport")))
      }
    } yield candidate
  }
}
