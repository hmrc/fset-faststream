/*
 * Copyright 2020 HM Revenue & Customs
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

package connectors.exchange.candidatescores

import models.UniqueIdentifier
import org.joda.time.DateTime
import play.api.libs.json.Json

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

                                     workingTogetherDevelopingSelfAndOthersScores: Option[WorkingTogetherDevelopingSelfAndOthersScores] = None,
                                     workingTogetherDevelopingSelfAndOthersAverage: Option[Double] = None,
                                     workingTogetherDevelopingSelfAndOthersFeedback: Option[String] = None,

                                     updatedBy: UniqueIdentifier,
                                     savedDate: Option[DateTime] = None,
                                     submittedDate: Option[DateTime] = None,
                                     version: Option[String] = None
)

object AssessmentScoresExercise {
  import models.FaststreamImplicits._
  implicit val scoresAndFeedbackFormat = Json.format[AssessmentScoresExercise]
}
