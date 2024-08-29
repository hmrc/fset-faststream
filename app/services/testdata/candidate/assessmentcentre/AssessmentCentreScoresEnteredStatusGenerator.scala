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
import model.assessmentscores._
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import services.assessmentscores.AssessmentScoresService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{OffsetDateTime, ZoneId}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssessmentCentreScoresEnteredStatusGenerator @Inject() (val previousStatusGenerator: AssessmentCentreAllocationConfirmedStatusGenerator,
                                                              @Named("AssessorAssessmentScoresService")
                                                              assessorAssessmentScoresService: AssessmentScoresService
                                                             )(implicit ec: ExecutionContext) extends ConstructiveGenerator {

  val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  // The scores awarded to the candidate by assessor/reviewer
  def exercise1Sample(assessorOrReviewer: String) = AssessmentScoresExercise(
    attended = true,
    overallAverage = Some(4.0),
    updatedBy = updatedBy,
    seeingTheBigPictureScores = Some(SeeingTheBigPictureScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    makingEffectiveDecisionsScores = Some(MakingEffectiveDecisionsScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    communicatingAndInfluencingScores = Some(CommunicatingAndInfluencingScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    seeingTheBigPictureFeedback = Some("Strategic approach feedback " + assessorOrReviewer),
    makingEffectiveDecisionsFeedback = Some("Analysis and Decision feedback" + assessorOrReviewer),
    communicatingAndInfluencingFeedback = Some("Leading and communicating feedback" + assessorOrReviewer)
  )

  def exercise2Sample(assessorOrReviewer: String) = AssessmentScoresExercise(
    attended = true,
    overallAverage = Some(4.0),
    updatedBy = updatedBy,
    makingEffectiveDecisionsScores = Some(MakingEffectiveDecisionsScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    adaptsScores = Some(AdaptsScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    communicatingAndInfluencingScores = Some(CommunicatingAndInfluencingScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    makingEffectiveDecisionsFeedback = Some("Analysis and Decision feedback" + assessorOrReviewer),
    adaptsFeedback = Some("Building Productive feedback" + assessorOrReviewer),
    communicatingAndInfluencingFeedback = Some("Leading and communicating feedback" + assessorOrReviewer)
  )

  def exercise3Sample(assessorOrReviewer: String) = AssessmentScoresExercise(
    attended = true,
    overallAverage = Some(4.0),
    updatedBy = updatedBy,
    adaptsScores = Some(AdaptsScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    communicatingAndInfluencingScores = Some(CommunicatingAndInfluencingScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    seeingTheBigPictureScores = Some(SeeingTheBigPictureScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    adaptsFeedback = Some("Building Productive feedback " + assessorOrReviewer),
    communicatingAndInfluencingFeedback = Some("Leading and communicating feedback " + assessorOrReviewer),
    seeingTheBigPictureFeedback = Some("Strategic approach feedback " + assessorOrReviewer)
  )

  def finalFeedbackSample(assessorOrReviewer: String) = AssessmentScoresFinalFeedback(
    "final feedback for " + assessorOrReviewer, updatedBy, OffsetDateTime.now(ZoneId.of("UTC"))
  )

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[CreateCandidateResponse] = {

    import model.command.AssessmentScoresCommands.AssessmentScoresSectionType._
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = UniqueIdentifier(candidateInPreviousStatus.applicationId.getOrElse(sys.error("Missed application id for candidate")))
      assessorOrReviewer = "assessor"
      _ <- assessorAssessmentScoresService.submitExercise(appId, exercise1, exercise1Sample(assessorOrReviewer))
      _ <- assessorAssessmentScoresService.submitExercise(
        appId, exercise2, exercise2Sample(assessorOrReviewer)
      )
      _ <- assessorAssessmentScoresService.submitExercise(
        appId, exercise3, exercise3Sample(assessorOrReviewer)
      )
      _ <- assessorAssessmentScoresService.submitFinalFeedback(appId, finalFeedbackSample(assessorOrReviewer))
    } yield {
      candidateInPreviousStatus
    }
  }
}
