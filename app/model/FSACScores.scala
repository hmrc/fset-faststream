/*
 * Copyright 2017 HM Revenue & Customs
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

package model

import org.joda.time.{ DateTime }
import play.api.libs.json.{ Json }

object FSACScores {


  case class FSACExerciseScoresAndFeedback(
                                            //  attended: Boolean,
                                            //  assessmentIncomplete: Boolean,

                                            seeingTheBigPicture: Option[Double] = None,
                                            changingAndImproving: Option[Double] = None,
                                            makingEffectiveDecisions: Option[Double] = None,
                                            leadingAndCommunicating: Option[Double] = None,
                                            collaboratingAndPartnering: Option[Double] = None,
                                            buildingCapabilityForAll: Option[Double] = None,
                                            achievingCommercialOutcomes: Option[Double] = None,
                                            deliveringValueForMoney: Option[Double] = None,

                                            strategicApproachToObjectivesFeedback: Option[String] = None,
                                            analysisAndDecisionMakingFeedback: Option[String] = None,
                                            leadingAndCommunicatingFeedback: Option[String] = None,
                                            buildingProductiveRelationshipsAndDevelopingCapabilityFeedback: Option[String] = None,

                                            strategicApproachToObjectivesAverage: Option[Double] = None,
                                            analysisAndDecisionMakingAverage: Option[Double] = None,
                                            leadingAndCommunicatingAverage: Option[Double] = None,
                                            buildingProductiveRelationshipsAndDevelopingCapabilityAverage: Option[Double] = None,
                                            //  updatedBy: UniqueIdentifier,
                                            //  savedDate: Option[DateTime] = None,
                                            submittedDate: Option[DateTime] = None,
                                            version: Option[String] = None
                                          ) {
    def isSubmitted = submittedDate.isDefined
  }

  object FSACExerciseScoresAndFeedback {
    implicit val format = Json.format[FSACExerciseScoresAndFeedback]
  }

  case class FSACAllExercisesScoresAndFeedback(
                                                applicationId: UniqueIdentifier,
                                                analysisExercise: Option[FSACExerciseScoresAndFeedback] = None,
                                                groupExercise: Option[FSACExerciseScoresAndFeedback] = None,
                                                leadershipExercise: Option[FSACExerciseScoresAndFeedback] = None
                                       )

  object FSACAllExercisesScoresAndFeedback {
    implicit val format = Json.format[FSACAllExercisesScoresAndFeedback]
  }


//  case class RecordCandidateScores(firstName: String, lastName: String, venueName: String, date: LocalDate)
/*
  case class CandidateScores(
    interview: Option[Double] = None,
    groupExercise: Option[Double] = None,
    writtenExercise: Option[Double] = None
  ) {
    def sum = (BigDecimal(interview.getOrElse(0.0)) + BigDecimal(groupExercise.getOrElse(0.0)) +
      BigDecimal(writtenExercise.getOrElse(0.0))).toDouble

    def length = List(interview, groupExercise, writtenExercise).flatten.length
  }

  case class CandidateScoreFeedback(
    interviewFeedback: Option[String] = None,
    groupExerciseFeedback: Option[String] = None,
    writtenExerciseFeedback: Option[String] = None
  )

  case class CandidateScoresAndFeedback(
    applicationId: String,
    attendancy: Option[Boolean],
    assessmentIncomplete: Boolean,
    leadingAndCommunicating: CandidateScores = CandidateScores(),
    collaboratingAndPartnering: CandidateScores = CandidateScores(),
    deliveringAtPace: CandidateScores = CandidateScores(),
    makingEffectiveDecisions: CandidateScores = CandidateScores(),
    changingAndImproving: CandidateScores = CandidateScores(),
    buildingCapabilityForAll: CandidateScores = CandidateScores(),
    motivationFit: CandidateScores = CandidateScores(),
    feedback: CandidateScoreFeedback = CandidateScoreFeedback()
  ) {
    def allScoresWithWeightOne = List(leadingAndCommunicating, collaboratingAndPartnering, deliveringAtPace,
      makingEffectiveDecisions, changingAndImproving, buildingCapabilityForAll)
  }

  //case class ApplicationScores(candidate: RecordCandidateScores, scoresAndFeedback: Option[CandidateScoresAndFeedback])
*/
 // object Implicits {
    //implicit val RecordCandidateScoresFormats: Format[RecordCandidateScores] = Json.format[RecordCandidateScores]
    //implicit val CandidateScoresFormats: Format[CandidateScores] = Json.format[CandidateScores]
    //implicit val CandidateScoreFeedbackFormats: Format[CandidateScoreFeedback] = Json.format[CandidateScoreFeedback]
  //  implicit val CandidateScoresAndFeedbackFormats: Format[CandidateScoresAndFeedback] = Json.format[CandidateScoresAndFeedback]
    //implicit val ApplicationScoresFormats: Format[ApplicationScores] = Json.format[ApplicationScores]
    //implicit val fSACAllExercisesScoresAndFeedbackFormat = Json.format[FSACAllExercisesScoresAndFeedback]
    //implicit val fSACExerciseScoresAndFeedbackFormat = Json.format[FSACExerciseScoresAndFeedback]
  //}

}
