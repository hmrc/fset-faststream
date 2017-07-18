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

package model.command

import model.FSACScores.{ FSACAllExercisesScoresAndFeedback, FSACExerciseScoresAndFeedback }
import model.UniqueIdentifier
import model.command.FSACScoresCommands.AssessmentExercise.AssessmentExercise
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

object FSACScoresCommands {

  // TODO MIGUEL: Rename to something like CandidateSummaryForFSACSCoresResponse
  case class RecordCandidateScores(firstName: String, lastName: String, venueName: String, fsacDate: LocalDate)
  object RecordCandidateScores {
    implicit val RecordCandidateScoresFormats: Format[RecordCandidateScores] = Json.format[RecordCandidateScores]
  }
/*
  case class CandidateScoresAndFeedback(
      applicationId: String,
      interview: Option[ScoresAndFeedback] = None,
      groupExercise: Option[ScoresAndFeedback] = None,
      writtenExercise: Option[ScoresAndFeedback] = None
  ) {
    def allVersionsEmpty: Boolean = List(
      interview.flatMap(_.version),
      groupExercise.flatMap(_.version),
      writtenExercise.flatMap(_.version)
    ).forall(_.isEmpty)

    def setVersion(newVersion: Option[String]): CandidateScoresAndFeedback = {
      def updateVersion(sOpt: Option[ScoresAndFeedback]): Option[ScoresAndFeedback] = {
        sOpt.map(_.copy(version = newVersion))
      }

      this.copy(
        interview = updateVersion(interview),
        groupExercise = updateVersion(groupExercise),
        writtenExercise = updateVersion(writtenExercise)
      )
    }

    def leadingAndCommunicatingAvg: Double = {
      average(List(interview, groupExercise, writtenExercise).flatMap(_.flatMap(_.leadingAndCommunicating)), 3)
    }

    def deliveringAtPaceAvg: Double = {
      average(List(interview, writtenExercise).flatMap(_.flatMap(_.deliveringAtPace)), 2)
    }

    def changingAndImprovingAvg: Double = {
      average(List(interview, writtenExercise).flatMap(_.flatMap(_.changingAndImproving)), 2)
    }

    def buildingCapabilityForAllAvg: Double = {
      average(List(interview, groupExercise).flatMap(_.flatMap(_.buildingCapabilityForAll)), 2)
    }

    def collaboratingAndPartneringAvg: Double = {
      average(List(groupExercise, writtenExercise).flatMap(_.flatMap(_.collaboratingAndPartnering)), 2)
    }

    def makingEffectiveDecisionsAvg: Double = {
      average(List(groupExercise, writtenExercise).flatMap(_.flatMap(_.makingEffectiveDecisions)), 2)
    }

    // It has weight equals 2 - no need to divide by 2
    // sum * 2 / 2 = sum
    def motivationalFitDoubledAvg: Double = {
      average(List(interview, groupExercise).flatMap(_.flatMap(_.motivationFit)), 1)
    }

    private def average(list: List[Double], mandatoryNumberOfElements: Int) = {
      (list.map(BigDecimal(_)).sum / mandatoryNumberOfElements).toDouble
    }
  }

  object CandidateScoresAndFeedback {
    val Interview = "interview"
    val GroupExercise = "groupExercise"
    val WrittenExercise = "writtenExercise"
    implicit val CandidateScoresAndFeedbackFormats: Format[CandidateScoresAndFeedback] = Json.format[CandidateScoresAndFeedback]
  }*/

  object AssessmentExercise extends Enumeration {
    type AssessmentExercise = Value

    val analysis, group, leadership = Value

    implicit val assessmentExerciseFormat = new Format[AssessmentExercise] {
      def reads(json: JsValue) = JsSuccess(AssessmentExercise.withName(json.as[String]))

      def writes(scheme: AssessmentExercise) = JsString(scheme.toString)
    }

    implicit object BSONEnumHandler extends BSONHandler[BSONString, AssessmentExercise] {
      def read(doc: BSONString) = AssessmentExercise.withName(doc.value)

      def write(scheme: AssessmentExercise) = BSON.write(scheme.toString)
    }
  }


  // TODO MIGUEL: Rename to FSACSCoresSubmitRequest
  case class ExerciseScoresAndFeedback(
    applicationId: UniqueIdentifier,
    exercise: AssessmentExercise,
    scoresAndFeedback: FSACExerciseScoresAndFeedback
  )

  object ExerciseScoresAndFeedback {
    implicit val exerciseScoresAndFeedbackFormats: Format[ExerciseScoresAndFeedback] = Json.format[ExerciseScoresAndFeedback]
  }

/*
  case class ScoresAndFeedback(attended: Boolean,
                               assessmentIncomplete: Boolean,
                               leadingAndCommunicating: Option[Double] = None,
                               collaboratingAndPartnering: Option[Double] = None,
                               deliveringAtPace: Option[Double] = None,
                               makingEffectiveDecisions: Option[Double] = None,
                               changingAndImproving: Option[Double] = None,
                               buildingCapabilityForAll: Option[Double] = None,
                               motivationFit: Option[Double] = None,
                               feedback: Option[String] = None,
                               updatedBy: String,
                               savedDate: Option[DateTime] = None,
                               submittedDate: Option[DateTime] = None,
                               version: Option[String] = None
                              )

  object ScoresAndFeedback {
    implicit val scoresAndFeedbackFormats: Format[ScoresAndFeedback] = Json.format[ScoresAndFeedback]
  }
*/
  // TODO MIGUEL: Rename to something like ApplicationFSACScores or FSACScoresWithCandidateSummary
  case class ApplicationScores(candidate: RecordCandidateScores, scoresAndFeedback: Option[FSACAllExercisesScoresAndFeedback])
  object ApplicationScores {
    implicit val ApplicationScoresFormats: Format[ApplicationScores] = Json.format[ApplicationScores]
  }
}
