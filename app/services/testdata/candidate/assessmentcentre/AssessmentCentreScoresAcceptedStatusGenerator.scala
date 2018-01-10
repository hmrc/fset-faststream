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

package services.testdata.candidate.assessmentcentre

import model.UniqueIdentifier
import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresFinalFeedback }
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.CreateCandidateData.CreateCandidateData
import org.joda.time.{ DateTime, DateTimeZone }
import play.api.mvc.RequestHeader
import services.assessmentscores.{ AssessmentScoresService, ReviewerAssessmentScoresService }
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object AssessmentCentreScoresAcceptedStatusGenerator extends AssessmentCentreScoresAcceptedStatusGenerator {
  override val previousStatusGenerator = AssessmentCentreScoresEnteredStatusGenerator
  override val reviewerAssessmentScoresService = ReviewerAssessmentScoresService
}

trait AssessmentCentreScoresAcceptedStatusGenerator extends ConstructiveGenerator {
  val reviewerAssessmentScoresService: AssessmentScoresService

  val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  private val now = DateTime.now(DateTimeZone.UTC)
  val finalFeedbackSample = AssessmentScoresFinalFeedback("feedback2", updatedBy, now)

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = UniqueIdentifier(candidateInPreviousStatus.applicationId.getOrElse(sys.error("Missed application id for candidate")))
      _ <- reviewerAssessmentScoresService.save(AssessmentScoresAllExercises(appId,
        Some(AssessmentCentreScoresEnteredStatusGenerator.analysisExerciseSample.copy(submittedDate = Some(now))),
        Some(AssessmentCentreScoresEnteredStatusGenerator.groupExerciseSample.copy(submittedDate = Some(now))),
        Some(AssessmentCentreScoresEnteredStatusGenerator.leadershipExerciseSample.copy(submittedDate = Some(now))),
        Some(finalFeedbackSample)
      ))
    } yield {
      candidateInPreviousStatus
    }
  }
}