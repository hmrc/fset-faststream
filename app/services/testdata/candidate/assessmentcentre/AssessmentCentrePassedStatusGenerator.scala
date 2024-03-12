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

import javax.inject.{Inject, Singleton}
import model.{Schemes, UniqueIdentifier}
import model.exchange.passmarksettings._
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import org.joda.time.{DateTime, DateTimeZone}
import play.api.mvc.RequestHeader
import repositories.SchemeRepository
import services.assessmentcentre.AssessmentCentreService
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssessmentCentrePassedStatusGenerator @Inject() (val previousStatusGenerator: AssessmentCentreScoresAcceptedStatusGenerator,
                                                       applicationAssessmentService: AssessmentCentreService,
                                                       passmarkService: AssessmentCentrePassMarkSettingsService,
                                                       schemeRepository: SchemeRepository
                                                      )(implicit ec: ExecutionContext) extends ConstructiveGenerator with Schemes {

  val updatedBy = UniqueIdentifier.randomUniqueIdentifier
  val version = UniqueIdentifier.randomUniqueIdentifier

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[CreateCandidateResponse] = {

    // Edip & Sdip do not go through FSAC so exclude them
    val schemes = schemeRepository.schemes.filterNot(scheme => scheme.id == Sdip || scheme.id == Edip).map(_.id).toList

    val tdgPassmarks = AssessmentCentrePassMarkSettingsPersistence(
      schemes.map(schemeId =>
        AssessmentCentreExercisePassMark(schemeId, AssessmentCentreExercisePassMarkThresholds(
          writtenExercise = PassMarkThreshold(0.2, 0.4),
          teamExercise = PassMarkThreshold(0.2, 0.4),
          leadershipExercise = PassMarkThreshold(0.2, 0.4),
          overall = PassMarkThreshold(2.0, 10.0)
        ))
      ),
      version.toString(),
      DateTime.now(DateTimeZone.UTC),
      updatedBy.toString()
    )

    for {
      latestPassMarks <- passmarkService.getLatestPassMarkSettings
      _ <- if (latestPassMarks.isEmpty) {
        passmarkService.createPassMarkSettings(tdgPassmarks)
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
