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

package services.testdata.candidate.sift

import javax.inject.{Inject, Provider, Singleton}
import model.ApplicationStatus.ApplicationStatus
import model.command.ApplicationForSift
import model.exchange.testdata.CreateCandidateResponse.{CreateCandidateResponse, SiftForm}
import model.persisted.SchemeEvaluationResult
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import model.{ApplicationRoute, ApplicationStatus, EvaluationResults}
import play.api.{Logger, Logging}
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import services.sift.ApplicationSiftService
import services.testdata.candidate._
import services.testdata.candidate.onlinetests.{Phase1TestsPassedNotifiedStatusGenerator, Phase3TestsPassedNotifiedStatusGenerator}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//object SiftEnteredStatusGenerator extends SiftEnteredStatusGenerator {
//  override val previousStatusGenerator = Phase3TestsPassedNotifiedStatusGenerator
//  override val appRepo = applicationRepository
//  override val siftService = ApplicationSiftService
//}
@Singleton
class SiftEnteredStatusGenerator @Inject() (val previousStatusGenerator: Phase3TestsPassedNotifiedStatusGenerator,
                                            fastPassAcceptedStatusGenerator: FastPassAcceptedStatusGenerator,
                                            phase1TestsPassedNotifiedStatusGenerator: Phase1TestsPassedNotifiedStatusGenerator,
                                            phase3TestsPassedNotifiedStatusGenerator2: Phase3TestsPassedNotifiedStatusGenerator,
                                            candidateStatusGeneratorFactory: Provider[CandidateStatusGeneratorFactory],
                                            appRepo: GeneralApplicationRepository,
                                            siftService: ApplicationSiftService
                                           ) extends ConstructiveGenerator with Logging {
//  val appRepo: GeneralApplicationRepository
//  val siftService: ApplicationSiftService

  override def getPreviousStatusGenerator(generatorConfig: CreateCandidateData): (ApplicationStatus, BaseGenerator) = {
    val previousStatusAndGeneratorPair = generatorConfig.statusData.previousApplicationStatus.map(previousApplicationStatus => {
      val generator = candidateStatusGeneratorFactory.get().getGenerator(
          generatorConfig.copy(
            statusData = generatorConfig.statusData.copy(
              applicationStatus = previousApplicationStatus
            )))
        (previousApplicationStatus, generator)
      }
    )
    previousStatusAndGeneratorPair.getOrElse(
      if (generatorConfig.hasFastPass) {
        (ApplicationStatus.FAST_PASS_ACCEPTED, fastPassAcceptedStatusGenerator)
      } else if (generatorConfig.statusData.applicationRoute == ApplicationRoute.Edip ||
        generatorConfig.statusData.applicationRoute == ApplicationRoute.Sdip /*||
      (generatorConfig.statusData.applicationRoute == ApplicationRoute.SdipFaststream &&
        generatorConfig.statusData.previousApplicationStatus == Some(Phase1))*/ ) {
        (ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED, phase1TestsPassedNotifiedStatusGenerator)
      } else {
        (ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED, phase3TestsPassedNotifiedStatusGenerator2)
      }
    )
  }

  private def getSchemesResults(candidateInPreviousStatus: CreateCandidateResponse,
                                generatorConfig: CreateCandidateData): List[SchemeEvaluationResult] = {
    if (generatorConfig.hasFastPass) {
      generatorConfig.schemeTypes.getOrElse(Nil).map(schemeType => SchemeEvaluationResult(schemeType, "Green"))
    } else if (List(ApplicationRoute.Sdip, ApplicationRoute.Edip).contains(generatorConfig.statusData.applicationRoute)) {
      candidateInPreviousStatus.phase1TestGroup.get.schemeResult.get.result
    } else {
      candidateInPreviousStatus.phase3TestGroup.get.schemeResult.get.result
    }
  }

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    for {
      (previousApplicationStatus, previousStatusGenerator) <- Future.successful(getPreviousStatusGenerator(generatorConfig))
      _ <- Future.successful(logger.error(s"previousApplicationStatus=$previousApplicationStatus, " +
        s"previousStatusGenerator=$previousStatusGenerator."))
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- siftService.progressApplicationToSiftStage(Seq(ApplicationForSift(candidateInPreviousStatus.applicationId.get,
        candidateInPreviousStatus.userId, previousApplicationStatus, getSchemesResults(candidateInPreviousStatus, generatorConfig))))
      _ <- siftService.saveSiftExpiryDate(candidateInPreviousStatus.applicationId.get)
    } yield {

      val greenSchemes = if (generatorConfig.hasFastPass) {
        generatorConfig.schemeTypes.map(_.map(schemeType => SchemeEvaluationResult(schemeType.toString(),
          EvaluationResults.Green.toString)))
      } else {
        if (List(ApplicationRoute.Sdip, ApplicationRoute.Edip).contains(generatorConfig.statusData.applicationRoute)) {
          candidateInPreviousStatus.phase1TestGroup.flatMap(tg =>
            tg.schemeResult.map(pm =>
              pm.result.filter(_.result == EvaluationResults.Green.toString)
            )
          )
        } else {
          candidateInPreviousStatus.phase3TestGroup.flatMap(tg =>
            tg.schemeResult.map(pm =>
              pm.result.filter(_.result == EvaluationResults.Green.toString)
            )
          )
        }
      }
      candidateInPreviousStatus.copy(
        siftForms = greenSchemes.map(_.map(result => SiftForm(result.schemeId, "", None)))
      )
    }
  }
}
