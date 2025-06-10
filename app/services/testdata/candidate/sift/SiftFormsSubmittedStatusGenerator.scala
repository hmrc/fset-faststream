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

package services.testdata.candidate.sift

import common.FutureEx

import javax.inject.{Inject, Singleton}
import model.exchange.sift.{GeneralQuestionsAnswers, SchemeSpecificAnswer}
import model.exchange.testdata.CreateCandidateResponse.{CreateCandidateResponse, TestGroupResponse, TestGroupResponse2}
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import model.{ApplicationRoute, SchemeId, SiftRequirement}
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories.SchemeRepository
import services.sift.SiftAnswersService
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.faker.DataFaker
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SiftFormsSubmittedStatusGenerator @Inject() (val previousStatusGenerator: SiftEnteredStatusGenerator,
                                                   siftService: SiftAnswersService,
                                                   schemeRepo: SchemeRepository,
                                                   dataFaker: DataFaker
                                                  )(implicit ec: ExecutionContext) extends ConstructiveGenerator with Logging {

  private def generateGeneralAnswers = GeneralQuestionsAnswers(
    multipleNationalities = false,
    secondNationality = None,
    nationality = "British",
    undergradDegree = None, postgradDegree = None
  )

  private def generateSchemeAnswers = SchemeSpecificAnswer(dataFaker.loremIpsum)

  private def saveSchemeAnswersFromFastPass(appId: String, createCandidateData: CreateCandidateData): Future[List[Unit]] = {
    createCandidateData.schemeTypes.map(schemeTypes =>
      FutureEx.traverseSerial(schemeTypes) { schemeType =>
        schemeRepo.schemes.find(_.id == schemeType).map { scheme =>
          if (scheme.siftRequirement.contains(SiftRequirement.FORM)) {
            siftService.addSchemeSpecificAnswer(appId, scheme.id, generateSchemeAnswers)
          } else {
            Future.successful(())
          }
        }.getOrElse(Future.successful(()))
      }
    ).getOrElse(Future.successful(Nil))
  }

  // TODO: Once phase3TestData is converted into TestGroupResponse2, merge this method with saveSchemeAnswersFromPhase3TestData
  private def saveSchemeAnswersFromPhase1TestData(appId: String, phase1TestData: TestGroupResponse2): Future[List[Unit]] = {
    phase1TestData.schemeResult.map { sr =>
      FutureEx.traverseSerial(sr.result) { result =>
        schemeRepo.schemes.find(_.id == result.schemeId).map { scheme =>
          if (scheme.siftRequirement.contains(SiftRequirement.FORM)) {
            siftService.addSchemeSpecificAnswer(appId, scheme.id, generateSchemeAnswers)
          } else {
            Future.successful(())
          }
        }.getOrElse(Future.successful(()))
      }
    }.getOrElse(Future.successful(Nil))
  }

  private def saveSchemeAnswersFromPhase3TestData(appId: String, phase3TestData: TestGroupResponse): Future[List[Unit]] = {
    phase3TestData.schemeResult.map { sr =>
      FutureEx.traverseSerial(sr.result) { result =>
        schemeRepo.schemes.find(_.id == result.schemeId).map { scheme =>
          if (scheme.siftRequirement.contains(SiftRequirement.FORM)) {
            siftService.addSchemeSpecificAnswer(appId, scheme.id, generateSchemeAnswers)
          } else {
            Future.successful(())
          }
        }.getOrElse(Future.successful(()))
      }
    }.getOrElse(Future.successful(Nil))
  }

  private def saveSchemeAnswers(generatorConfig: CreateCandidateData, candidateInPreviousStatus: CreateCandidateResponse) = {
    if (generatorConfig.hasFastPass) {
      saveSchemeAnswersFromFastPass(candidateInPreviousStatus.applicationId.get,
        generatorConfig)
    } else if (List(ApplicationRoute.Sdip, ApplicationRoute.Edip).contains(generatorConfig.statusData.applicationRoute)) {
      saveSchemeAnswersFromPhase1TestData(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.phase1TestGroup.get)
    } else {
      saveSchemeAnswersFromPhase3TestData(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.phase3TestGroup.get)
    }
  }

  private def requiresSift(createCandidateData: CreateCandidateData): Boolean = {
    val schemes = createCandidateData.schemeTypes.getOrElse(List.empty[SchemeId])
    val siftableSchemes = schemeRepo.siftableSchemeIds
    val siftableAndEvaluationRequiredSchemes = schemeRepo.siftableAndEvaluationRequiredSchemeIds
    val result = siftableSchemes.exists(schemes.contains) ||
      siftableAndEvaluationRequiredSchemes.exists(schemes.contains)
    logger.warn(s"TDG - SiftFormsSubmittedStatusGenerator - should go via this generator = $result. Schemes = $schemes")
    result
  }

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[CreateCandidateResponse] = {
    if (requiresSift(generatorConfig)) {
      for {
        candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
        _ <- siftService.addGeneralAnswers(candidateInPreviousStatus.applicationId.get, generateGeneralAnswers)
        _ <- saveSchemeAnswers(generatorConfig, candidateInPreviousStatus)
        _ <- siftService.submitAnswers(candidateInPreviousStatus.applicationId.get)
      } yield {
        candidateInPreviousStatus
      }
    } else {
      for {
        candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      } yield {
        candidateInPreviousStatus
      }
    }
  }
}
