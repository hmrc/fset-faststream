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

package model.assessmentscores

import model.UniqueIdentifier
import play.api.libs.json.{Json, OFormat}

import java.time.OffsetDateTime

case class AssessmentScoresExercise(
                                     attended: Boolean,

                                     seeingTheBigPictureScores: Option[SeeingTheBigPictureScores] = None,
                                     seeingTheBigPictureAverage: Option[Double] = None,
                                     seeingTheBigPictureFeedback: Option[String] = None,

                                     makingEffectiveDecisionsScores: Option[MakingEffectiveDecisionsScores] = None,
                                     makingEffectiveDecisionsAverage: Option[Double] = None,
                                     makingEffectiveDecisionsFeedback: Option[String] = None,

                                     communicatingAndInfluencingScores: Option[CommunicatingAndInfluencingScores] = None,
                                     communicatingAndInfluencingAverage: Option[Double] = None,
                                     communicatingAndInfluencingFeedback: Option[String] = None,

                                     workingTogetherDevelopingSelfAndOthersScores: Option[WorkingTogetherDevelopingSelfAndOtherScores] = None,
                                     workingTogetherDevelopingSelfAndOthersAverage: Option[Double] = None,
                                     workingTogetherDevelopingSelfAndOthersFeedback: Option[String] = None,

                                     updatedBy: UniqueIdentifier,
                                     savedDate: Option[OffsetDateTime] = None,
                                     submittedDate: Option[OffsetDateTime] = None,
                                     version: Option[String] = None
) extends AssessmentScoresSection {
  def isSubmitted = submittedDate.isDefined

  override def toString: String = {
    s"attended:$attended," +
    s"seeingTheBigPictureScores:$seeingTheBigPictureScores," +
    s"seeingTheBigPictureAverage:$seeingTheBigPictureAverage," +
    s"seeingTheBigPictureFeedback:$seeingTheBigPictureFeedback," +
    s"makingEffectiveDecisionsScores:$makingEffectiveDecisionsScores," +
    s"makingEffectiveDecisionsAverage:$makingEffectiveDecisionsAverage," +
    s"makingEffectiveDecisionsFeedback:$makingEffectiveDecisionsFeedback," +
    s"communicatingAndInfluencingScores:$communicatingAndInfluencingScores," +
    s"communicatingAndInfluencingAverage:$communicatingAndInfluencingAverage," +
    s"communicatingAndInfluencingFeedback:$communicatingAndInfluencingFeedback," +
    s"workingTogetherDevelopingSelfAndOthersScores:$workingTogetherDevelopingSelfAndOthersScores," +
    s"workingTogetherDevelopingSelfAndOthersAverage:$workingTogetherDevelopingSelfAndOthersAverage," +
    s"workingTogetherDevelopingSelfAndOthersFeedback:$workingTogetherDevelopingSelfAndOthersFeedback," +
    s"updatedBy:$updatedBy," +
    s"savedDate:$savedDate," +
    s"submittedDate:$submittedDate," +
    s"version:$version"
  }

  def toExchange =
    AssessmentScoresExerciseExchange(
      attended,
      seeingTheBigPictureScores,
      seeingTheBigPictureAverage,
      seeingTheBigPictureFeedback,
      makingEffectiveDecisionsScores,
      makingEffectiveDecisionsAverage,
      makingEffectiveDecisionsFeedback,
      communicatingAndInfluencingScores,
      communicatingAndInfluencingAverage,
      communicatingAndInfluencingFeedback,
      workingTogetherDevelopingSelfAndOthersScores,
      workingTogetherDevelopingSelfAndOthersAverage,
      workingTogetherDevelopingSelfAndOthersFeedback,
      updatedBy,
      savedDate,
      submittedDate,
      version
    )
}

object AssessmentScoresExercise {
  import repositories.formats.MongoJavatimeFormats.Implicits._ // Needed to handle storing ISODate format in Mongo
  implicit val jsonFormat: OFormat[AssessmentScoresExercise] = Json.format[AssessmentScoresExercise]
}

case class AssessmentScoresExerciseExchange(
                                     attended: Boolean,

                                     seeingTheBigPictureScores: Option[SeeingTheBigPictureScores] = None,
                                     seeingTheBigPictureAverage: Option[Double] = None,
                                     seeingTheBigPictureFeedback: Option[String] = None,

                                     makingEffectiveDecisionsScores: Option[MakingEffectiveDecisionsScores] = None,
                                     makingEffectiveDecisionsAverage: Option[Double] = None,
                                     makingEffectiveDecisionsFeedback: Option[String] = None,

                                     communicatingAndInfluencingScores: Option[CommunicatingAndInfluencingScores] = None,
                                     communicatingAndInfluencingAverage: Option[Double] = None,
                                     communicatingAndInfluencingFeedback: Option[String] = None,

                                     workingTogetherDevelopingSelfAndOthersScores: Option[WorkingTogetherDevelopingSelfAndOtherScores] = None,
                                     workingTogetherDevelopingSelfAndOthersAverage: Option[Double] = None,
                                     workingTogetherDevelopingSelfAndOthersFeedback: Option[String] = None,

                                     updatedBy: UniqueIdentifier,
                                     savedDate: Option[OffsetDateTime] = None,
                                     submittedDate: Option[OffsetDateTime] = None,
                                     version: Option[String] = None
                                   ) extends AssessmentScoresSection {
  def toPersistence =
    AssessmentScoresExercise(
      attended,
      seeingTheBigPictureScores,
      seeingTheBigPictureAverage,
      seeingTheBigPictureFeedback,
      makingEffectiveDecisionsScores,
      makingEffectiveDecisionsAverage,
      makingEffectiveDecisionsFeedback,
      communicatingAndInfluencingScores,
      communicatingAndInfluencingAverage,
      communicatingAndInfluencingFeedback,
      workingTogetherDevelopingSelfAndOthersScores,
      workingTogetherDevelopingSelfAndOthersAverage,
      workingTogetherDevelopingSelfAndOthersFeedback,
      updatedBy,
      savedDate,
      submittedDate,
      version
    )

  override def toString: String = s"attended=$attended," +
    s"seeingTheBigPictureScores=$seeingTheBigPictureScores," +
    s"seeingTheBigPictureAverage=$seeingTheBigPictureAverage," +
    s"seeingTheBigPictureFeedback=$seeingTheBigPictureFeedback," +
    s"makingEffectiveDecisionsScores=$makingEffectiveDecisionsScores," +
    s"makingEffectiveDecisionsAverage=$makingEffectiveDecisionsAverage," +
    s"makingEffectiveDecisionsFeedback=$makingEffectiveDecisionsFeedback," +
    s"communicatingAndInfluencingScores=$communicatingAndInfluencingScores," +
    s"communicatingAndInfluencingAverage=$communicatingAndInfluencingAverage," +
    s"communicatingAndInfluencingFeedback=$communicatingAndInfluencingFeedback," +
    s"workingTogetherDevelopingSelfAndOthersScores=$workingTogetherDevelopingSelfAndOthersScores," +
    s"workingTogetherDevelopingSelfAndOthersAverage=$workingTogetherDevelopingSelfAndOthersAverage," +
    s"workingTogetherDevelopingSelfAndOthersFeedback=$workingTogetherDevelopingSelfAndOthersFeedback," +
    s"updatedBy=$updatedBy," +
    s"savedDate=$savedDate," +
    s"submittedDate=$submittedDate," +
    s"version=$version"
}

object AssessmentScoresExerciseExchange {
  implicit val jsonFormat: OFormat[AssessmentScoresExerciseExchange] = Json.format[AssessmentScoresExerciseExchange]
}
