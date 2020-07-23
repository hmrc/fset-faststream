/*
 * Copyright 2020 HM Revenue & Customs
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

package services.testdata.candidate.onlinetests.phase3

import common.FutureEx
import javax.inject.{ Inject, Singleton }
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories.onlinetesting.Phase3TestRepository
import services.onlinetesting.phase3.Phase3TestService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class Phase3TestsStartedStatusGenerator @Inject() (val previousStatusGenerator: Phase3TestsInvitedStatusGenerator,
                                                   otRepository: Phase3TestRepository,
                                                   otService: Phase3TestService
                                                  ) extends ConstructiveGenerator {
//  val otRepository: Phase3TestRepository
//  val otService: Phase3TestService

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- FutureEx.traverseSerial(candidate.phase3TestGroup.get.tests.map(_.token))(token =>
        otService.markAsStarted(token, generatorConfig.phase3TestData.flatMap(_.start).getOrElse(DateTime.now))
      )
    } yield candidate
  }
}
