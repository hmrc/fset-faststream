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
import org.joda.time.{DateTime, DateTimeZone}
import play.api.mvc.RequestHeader
import services.assessmentscores.AssessmentScoresService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssessmentCentreScoresEnteredStatusGenerator @Inject() (val previousStatusGenerator: AssessmentCentreAllocationConfirmedStatusGenerator,
                                                              @Named("AssessorAssessmentScoresService")
                                                              assessorAssessmentScoresService: AssessmentScoresService
                                                             )(implicit ec: ExecutionContext) extends ConstructiveGenerator {

  val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  // The scores awarded to the candidate by assessor/reviewer
  def analysisExerciseSample(assessorOrReviewer: String) = AssessmentScoresExercise(
    attended = true,
    makingEffectiveDecisionsAverage = Some(5.0),
    communicatingAndInfluencingAverage = Some(4.0),
    seeingTheBigPictureAverage = Some(4.0),
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

  def groupExerciseSample(assessorOrReviewer: String) = AssessmentScoresExercise(
    attended = true,
    makingEffectiveDecisionsAverage = Some(5.0),
    workingTogetherDevelopingSelfAndOthersAverage = Some(2.0),
    communicatingAndInfluencingAverage = Some(4.0),
    updatedBy = updatedBy,
    makingEffectiveDecisionsScores = Some(MakingEffectiveDecisionsScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    workingTogetherDevelopingSelfAndOthersScores = Some(WorkingTogetherDevelopingSelfAndOtherScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    communicatingAndInfluencingScores = Some(CommunicatingAndInfluencingScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    makingEffectiveDecisionsFeedback = Some("Analysis and Decision feedback" + assessorOrReviewer),
    workingTogetherDevelopingSelfAndOthersFeedback = Some("Building Productive feedback" + assessorOrReviewer),
    communicatingAndInfluencingFeedback = Some("Leading and communicating feedback" + assessorOrReviewer)
  )

  def leadershipExerciseSample(assessorOrReviewer: String) = AssessmentScoresExercise(
    attended = true,
    workingTogetherDevelopingSelfAndOthersAverage = Some(4.0),
    communicatingAndInfluencingAverage = Some(4.0),
    seeingTheBigPictureAverage = Some(4.0),
    updatedBy = updatedBy,
    workingTogetherDevelopingSelfAndOthersScores = Some(WorkingTogetherDevelopingSelfAndOtherScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    communicatingAndInfluencingScores = Some(CommunicatingAndInfluencingScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    seeingTheBigPictureScores = Some(SeeingTheBigPictureScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    workingTogetherDevelopingSelfAndOthersFeedback = Some("Building Productive feedback " + assessorOrReviewer),
    communicatingAndInfluencingFeedback = Some("Leading and communicating feedback " + assessorOrReviewer),
    seeingTheBigPictureFeedback = Some("Strategic approach feedback " + assessorOrReviewer)
  )

  def finalFeedbackSample(assessorOrReviewer: String) = AssessmentScoresFinalFeedback(
    "final feedback for " + assessorOrReviewer, updatedBy, DateTime.now(DateTimeZone.UTC)
  )

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[CreateCandidateResponse] = {

    import model.command.AssessmentScoresCommands.AssessmentScoresSectionType._
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = UniqueIdentifier(candidateInPreviousStatus.applicationId.getOrElse(sys.error("Missed application id for candidate")))
      assessorOrReviewer = "assessor"
      _ <- assessorAssessmentScoresService.submitExercise(appId, writtenExercise, analysisExerciseSample(assessorOrReviewer))
      _ <- assessorAssessmentScoresService.submitExercise(appId, teamExercise, groupExerciseSample(assessorOrReviewer))
      _ <- assessorAssessmentScoresService.submitExercise(appId, leadershipExercise, leadershipExerciseSample(assessorOrReviewer))
      _ <- assessorAssessmentScoresService.submitFinalFeedback(appId, finalFeedbackSample(assessorOrReviewer))
    } yield {
      candidateInPreviousStatus
    }
  }
}
