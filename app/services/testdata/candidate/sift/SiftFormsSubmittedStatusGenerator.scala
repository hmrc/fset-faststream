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

import common.FutureEx
import model.SiftRequirement
import model.exchange.sift.{GeneralQuestionsAnswers, SchemeSpecificAnswer}
import model.exchange.testdata.CreateCandidateResponse.{CreateCandidateResponse, TestGroupResponse}
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.{SchemeRepository, SchemeYamlRepository}
import services.sift.SiftAnswersService
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.faker.DataFaker
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SiftFormsSubmittedStatusGenerator extends SiftFormsSubmittedStatusGenerator {
  val previousStatusGenerator = SiftEnteredStatusGenerator
  val siftService = SiftAnswersService
  val schemeRepo = SchemeYamlRepository
}

trait SiftFormsSubmittedStatusGenerator extends ConstructiveGenerator {
  val siftService: SiftAnswersService
  val schemeRepo: SchemeRepository

  def generateGeneralAnswers = GeneralQuestionsAnswers(
    multipleNationalities = false,
    secondNationality = None,
    nationality = "British",
    undergradDegree = None, postgradDegree = None
  )

  def generateSchemeAnswers = SchemeSpecificAnswer(DataFaker.loremIpsum)

  def saveSchemeAnswersFromFastPass(appId: String, createCandidateData: CreateCandidateData): Future[List[Unit]] = {
    createCandidateData.schemeTypes.map(schemeTypes =>
      FutureEx.traverseSerial(schemeTypes) { schemeType =>
        schemeRepo.schemes.find(_.id == schemeType).map { scheme =>
          if (scheme.siftRequirement.contains(SiftRequirement.FORM)) {
            siftService.addSchemeSpecificAnswer(appId, scheme.id, generateSchemeAnswers)
          } else {
            Future()
          }
        }.getOrElse(Future())
      }
    ).getOrElse(Future.successful(Nil))
  }

  def saveSchemeAnswersFromPhase3(appId: String, p3: TestGroupResponse): Future[List[Unit]] = {
    p3.schemeResult.map { sr =>
      FutureEx.traverseSerial(sr.result) { result =>
        schemeRepo.schemes.find(_.id == result.schemeId).map { scheme =>
          if (scheme.siftRequirement.contains(SiftRequirement.FORM)) {
            siftService.addSchemeSpecificAnswer(appId, scheme.id, generateSchemeAnswers)
          } else {
            Future()
          }
        }.getOrElse(Future())
      }
    }.getOrElse(Future.successful(Nil))
  }

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- siftService.addGeneralAnswers(candidateInPreviousStatus.applicationId.get, generateGeneralAnswers)
      - <- if (generatorConfig.hasFastPass) {
        saveSchemeAnswersFromFastPass(candidateInPreviousStatus.applicationId.get,
          generatorConfig)
      } else {
        saveSchemeAnswersFromPhase3(candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.phase3TestGroup.get)
      }
      _ <- siftService.submitAnswers(candidateInPreviousStatus.applicationId.get)
    } yield {
      candidateInPreviousStatus
    }

  }
}
