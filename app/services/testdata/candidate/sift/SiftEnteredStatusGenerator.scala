/*
 * Copyright 2019 HM Revenue & Customs
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

package services.testdata.candidate.sift

import model.{ApplicationRoute, ApplicationStatus, EvaluationResults}
import model.command.ApplicationForSift
import model.exchange.testdata.CreateCandidateResponse.{CreateCandidateResponse, SiftForm}
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import services.sift.ApplicationSiftService
import services.testdata.candidate.{ConstructiveGenerator, SubmittedStatusGenerator}
import services.testdata.candidate.onlinetests.{Phase1TestsPassedNotifiedStatusGenerator, Phase3TestsPassedNotifiedStatusGenerator}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object SiftEnteredStatusGenerator extends SiftEnteredStatusGenerator {
  override val previousStatusGenerator = Phase3TestsPassedNotifiedStatusGenerator
  override val siftService = ApplicationSiftService
}

trait SiftEnteredStatusGenerator extends ConstructiveGenerator {
  val siftService: ApplicationSiftService

  override def getPreviousStatusGenerator(generatorConfig: CreateCandidateData): ConstructiveGenerator = {
    if (generatorConfig.hasFastPass) { SubmittedStatusGenerator }
    else if (generatorConfig.statusData.applicationRoute == ApplicationRoute.Edip ||
      generatorConfig.statusData.applicationRoute == ApplicationRoute.Sdip /*||
      (generatorConfig.statusData.applicationRoute == ApplicationRoute.SdipFaststream &&
        generatorConfig.statusData.previousApplicationStatus == Some(Phase1))*/) { Phase1TestsPassedNotifiedStatusGenerator }
    else { Phase3TestsPassedNotifiedStatusGenerator }
  }

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {
    for {
      candidateInPreviousStatus <- getPreviousStatusGenerator(generatorConfig).generate(generationId, generatorConfig)
      _ <- siftService.progressApplicationToSiftStage(Seq(ApplicationForSift(candidateInPreviousStatus.applicationId.get,
        candidateInPreviousStatus.userId,
        ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
        candidateInPreviousStatus.phase3TestGroup.get.schemeResult.get.result)))
      _ <- siftService.saveSiftExpiryDate(candidateInPreviousStatus.applicationId.get)
    } yield {

      val greenSchemes = candidateInPreviousStatus.phase3TestGroup.flatMap(tg =>
        tg.schemeResult.map(pm =>
          pm.result.filter(_.result == EvaluationResults.Green.toString)
        )
      )

      candidateInPreviousStatus.copy(
        siftForms = greenSchemes.map(_.map( result => SiftForm(result.schemeId, "", None) ))
      )
    }
  }
}
