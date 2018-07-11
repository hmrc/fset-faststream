/*
 * Copyright 2018 HM Revenue & Customs
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
import model.ApplicationStatus.ApplicationStatus
import model.command.ApplicationForSift
import model.exchange.testdata.CreateCandidateResponse.{CreateCandidateResponse, SiftForm}
import model.persisted.SchemeEvaluationResult
import model.testdata.CreateCandidateData.CreateCandidateData
import model.{ApplicationRoute, ApplicationStatus, EvaluationResults}
import play.api.mvc.RequestHeader
import services.sift.ApplicationSiftService
import services.testdata.candidate.onlinetests.Phase3TestsPassedNotifiedStatusGenerator
import services.testdata.candidate.onlinetests.phase1.Phase1TestsResultsReceivedStatusGenerator
import services.testdata.candidate.{BaseGenerator, ConstructiveGenerator}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SiftEnteredStatusGenerator extends SiftEnteredStatusGenerator {
  override val previousStatusGenerator = Phase3TestsPassedNotifiedStatusGenerator
  override val siftService = ApplicationSiftService
}

trait SiftEnteredStatusGenerator extends ConstructiveGenerator {
  val siftService: ApplicationSiftService

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    for {
      candidateInPreviousStatus <- getPreviousStatusGenerator(generatorConfig).generate(generationId, generatorConfig)
      _ <- siftService.progressApplicationToSiftStage(Seq(ApplicationForSift(candidateInPreviousStatus.applicationId.get,
        candidateInPreviousStatus.userId,
        getApplicationStatus(generatorConfig),
        getPreviousPhaseTestGroup(generatorConfig, candidateInPreviousStatus))))
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

  private def getPreviousStatusGenerator(generatorConfig: CreateCandidateData): BaseGenerator = {
    generatorConfig.statusData.applicationRoute match {
      case ApplicationRoute.Sdip => Phase1TestsResultsReceivedStatusGenerator // Sdip needs to skip some generators
      case _ => previousStatusGenerator // Use the default
    }
  }

  private def getApplicationStatus(generatorConfig: CreateCandidateData): ApplicationStatus = {
    generatorConfig.statusData.applicationRoute match {
      case ApplicationRoute.Sdip => ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED
      case _ => ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED
    }
  }

  private def getPreviousPhaseTestGroup(generatorConfig: CreateCandidateData,
                                        candidateInPreviousStatus: CreateCandidateResponse): Seq[SchemeEvaluationResult] = {
    generatorConfig.statusData.applicationRoute match {
      case ApplicationRoute.Sdip =>
        //scalastyle:off
        println(s"****************${candidateInPreviousStatus.phase1TestGroup}")
        candidateInPreviousStatus.phase1TestGroup.get.schemeResult.get.result
      case _ => candidateInPreviousStatus.phase3TestGroup.get.schemeResult.get.result
    }
  }
}