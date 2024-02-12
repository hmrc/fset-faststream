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

import com.google.inject.name.Named

import javax.inject.{Inject, Singleton}
import model.UniqueIdentifier
import model.assessmentscores.AssessmentScoresAllExercises
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import org.joda.time.{DateTime, DateTimeZone}
import play.api.mvc.RequestHeader
import services.assessmentscores.AssessmentScoresService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssessmentCentreScoresAcceptedStatusGenerator @Inject() (val previousStatusGenerator: AssessmentCentreScoresEnteredStatusGenerator,
                                                               @Named("ReviewerAssessmentScoresService")
                                                               reviewerAssessmentScoresService: AssessmentScoresService
                                                              )(implicit ec: ExecutionContext) extends ConstructiveGenerator {

  val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  private val now = DateTime.now(DateTimeZone.UTC)

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[CreateCandidateResponse] = {

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = UniqueIdentifier(candidateInPreviousStatus.applicationId.getOrElse(sys.error("Missed application id for candidate")))
      assessorOrReviewer = "reviewer"
      _ <- reviewerAssessmentScoresService.save(AssessmentScoresAllExercises(appId,
        Some(previousStatusGenerator.writtenExerciseSample(assessorOrReviewer).copy(submittedDate = Some(now))),
        Some(previousStatusGenerator.teamExerciseSample(assessorOrReviewer).copy(submittedDate = Some(now))),
        Some(previousStatusGenerator.leadershipExerciseSample(assessorOrReviewer).copy(submittedDate = Some(now))),
        Some(previousStatusGenerator.finalFeedbackSample(assessorOrReviewer))
      ))
    } yield {
      candidateInPreviousStatus
    }
  }
}
