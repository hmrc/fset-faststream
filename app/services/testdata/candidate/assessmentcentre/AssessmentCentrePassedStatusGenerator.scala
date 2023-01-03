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

package services.testdata.candidate.assessmentcentre

import javax.inject.{ Inject, Singleton }
import model.UniqueIdentifier
import model.exchange.passmarksettings._
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import org.joda.time.{ DateTime, DateTimeZone }
import play.api.mvc.RequestHeader
import repositories.SchemeRepository
import services.assessmentcentre.AssessmentCentreService
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class AssessmentCentrePassedStatusGenerator @Inject() (val previousStatusGenerator: AssessmentCentreScoresAcceptedStatusGenerator,
                                                       applicationAssessmentService: AssessmentCentreService,
                                                       passmarkService: AssessmentCentrePassMarkSettingsService,
                                                       schemeRepository: SchemeRepository
                                                      ) extends ConstructiveGenerator {

  val updatedBy = UniqueIdentifier.randomUniqueIdentifier
  val version = UniqueIdentifier.randomUniqueIdentifier

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    val schemes = schemeRepository.schemes.map(_.id).toList
    val dummyPassmarks = AssessmentCentrePassMarkSettings(
      schemes.map(schemeId =>
        AssessmentCentrePassMark(schemeId, AssessmentCentrePassMarkThresholds(
          seeingTheBigPicture = PassMarkThreshold(0.2, 0.4),
          makingEffectiveDecisions = PassMarkThreshold(0.2, 0.4),
          communicatingAndInfluencing = PassMarkThreshold(0.2, 0.4),
          workingTogetherDevelopingSelfAndOthers = PassMarkThreshold(0.2, 0.4),
          overall = PassMarkThreshold(2, 16)
        ))
      ),
      version.toString(),
      DateTime.now(DateTimeZone.UTC),
      updatedBy.toString()
    )

    for {
      latestPassMarks <- passmarkService.getLatestPassMarkSettings
      _ <- if (latestPassMarks.isEmpty) {
        passmarkService.createPassMarkSettings(dummyPassmarks)
      } else {
        Future.successful(())
      }
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = UniqueIdentifier(candidateInPreviousStatus.applicationId.getOrElse(sys.error("Missed application id for candidate")))
      passMarksSchemesAndScoresSeq <- applicationAssessmentService.nextAssessmentCandidatesReadyForEvaluation(100)
      scores = passMarksSchemesAndScoresSeq.find(_.scores.applicationId == appId)
        .getOrElse(sys.error(s"Candidate $appId is not ready for evaluation yet"))
      _ <- applicationAssessmentService.evaluateAssessmentCandidate(scores)
    } yield {
      candidateInPreviousStatus
    }
  }
}
