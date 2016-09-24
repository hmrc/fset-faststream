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
import repositories._
import repositories.application.OnlineTestRepository
import services.onlinetesting.OnlineTestService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Phase1TestsCompletedStatusGenerator extends Phase1TestsCompletedStatusGenerator {
  override val previousStatusGenerator = Phase1TestsStartedStatusGenerator
  override val otRepository = onlineTestRepository
  override val otService = OnlineTestService
}

trait Phase1TestsCompletedStatusGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository
  val otService: OnlineTestService

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    for {
<<<<<<< fc5307e58c1e54e17278ae244c8c898daa9c8c82
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- FutureEx.traverseSerial(candidate.phase1TestGroup.get.tests.map(_.cubiksUserId))(id => otService.markAsCompleted(id))
    } yield candidate
=======
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ = Logger.warn("=========== Completed Gen starting ============")
      _ <- otService.markAsCompleted(candidateInPreviousStatus.phase1TestGroup.get.tests.head.cubiksUserId)
      _ <- Future(candidateInPreviousStatus.phase1TestGroup.get.tests.lift(1).map { secondTest =>
        Logger.warn("1st Id = " + candidateInPreviousStatus.phase1TestGroup.get.tests.head.cubiksUserId)
        Logger.warn("2nd Id = " + secondTest.cubiksUserId)
        otService.markAsCompleted(secondTest.cubiksUserId).map(_ => ())
      })
    } yield {
      candidateInPreviousStatus
    }

>>>>>>> Update test data generators, slight refactors to online test services and repositories
  }
}
