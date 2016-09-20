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

import model.ApplicationStatuses
import repositories._
import repositories.application.GeneralApplicationRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object WithdrawnStatusGenerator extends WithdrawnStatusGenerator {
  override val appRepository = applicationRepository
}

trait WithdrawnStatusGenerator extends BaseGenerator {
  val appRepository: GeneralApplicationRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {

    for {
      candidateInPreviousStatus <- StatusGeneratorFactory.getGenerator(generatorConfig.previousStatus.getOrElse(
        ApplicationStatuses.Submitted
      ), None).generate(generationId, generatorConfig)
      _ <- appRepository.withdraw(candidateInPreviousStatus.applicationId.get, model.command.WithdrawApplication("test", Some("test"),
        "test"))
    } yield {
      candidateInPreviousStatus
    }
  }
}
