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

package services.assessmentscores

import factories.DateTimeFactory
import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresExercise }
import model.UniqueIdentifier
import model.command.AssessmentScoresCommands.{ AssessmentExerciseType, AssessmentScoresFindResponse, RecordCandidateScores }
import model.persisted.eventschedules.Event
import play.api.mvc.RequestHeader
import repositories.events.EventsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.{ AssessmentScoresRepository, CandidateAllocationMongoRepository }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AssessmentScoresService extends AssessmentScoresService {
  override val assessmentScoresRepository: AssessmentScoresRepository = repositories.assessmentScoresRepository
  override val candidateAllocationRepository: CandidateAllocationMongoRepository = repositories.candidateAllocationRepository
  override val eventsRepository: EventsRepository = repositories.eventsRepository
  override val personalDetailsRepository: PersonalDetailsRepository = repositories.personalDetailsRepository

  override val dateTimeFactory = DateTimeFactory
}

trait AssessmentScoresService {
  val assessmentScoresRepository: AssessmentScoresRepository
  val candidateAllocationRepository: CandidateAllocationMongoRepository
  val eventsRepository: EventsRepository
  val personalDetailsRepository: PersonalDetailsRepository

  val dateTimeFactory: DateTimeFactory

  def save(scores: AssessmentScoresAllExercises)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val scoresWithSubmittedDate = scores.copy(
      analysisExercise = scores.analysisExercise.map(_.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone))),
      groupExercise = scores.groupExercise.map(_.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone))),
      leadershipExercise = scores.leadershipExercise.map(_.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone)))
    )
    assessmentScoresRepository.save(scoresWithSubmittedDate)
  }

  def saveExercise(applicationId: UniqueIdentifier,
                   assessmentExerciseType: AssessmentExerciseType.AssessmentExerciseType,
                   newExerciseScores: AssessmentScoresExercise): Future[Unit] = {
    def updateAllExercisesWithExercise(oldAllExercisesScores: AssessmentScoresAllExercises,
                                       newExerciseScoresWithSubmittedDate: AssessmentScoresExercise) = {
      assessmentExerciseType match {
        case AssessmentExerciseType.analysisExercise =>
          oldAllExercisesScores.copy(analysisExercise = Some(newExerciseScoresWithSubmittedDate))
        case AssessmentExerciseType.groupExercise =>
          oldAllExercisesScores.copy(groupExercise = Some(newExerciseScoresWithSubmittedDate))
        case AssessmentExerciseType.leadershipExercise =>
          oldAllExercisesScores.copy(leadershipExercise = Some(newExerciseScoresWithSubmittedDate))
      }
    }

    (for {
      oldAllExercisesScoresMaybe <- assessmentScoresRepository.find(applicationId)
      oldAllExercisesScores = oldAllExercisesScoresMaybe.getOrElse(AssessmentScoresAllExercises(applicationId, None, None, None))
      newExerciseScoresWithSubmittedDate = newExerciseScores.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone))
      newAllExercisesScores = updateAllExercisesWithExercise(oldAllExercisesScores, newExerciseScoresWithSubmittedDate)
      _ <- assessmentScoresRepository.save(newAllExercisesScores)
    } yield {
      ()
    })
  }

  def findAssessmentScoresWithCandidateSummaryByApplicationId(applicationId: UniqueIdentifier): Future[AssessmentScoresFindResponse] = {
    // TODO: return Option. if assessmentScores are not found, return None
    for {
      candidateAllocations <- candidateAllocationRepository.find(applicationId.toString())
      candidateAllocation = candidateAllocations.head
      event <- eventsRepository.getEvent(candidateAllocation.eventId)
      assessmentScoresWithCandidateSummary <- findOneAssessmentScoresWithCandidateSummaryByApplicationId(
        applicationId,
        event,
        UniqueIdentifier(candidateAllocation.sessionId))
    } yield {
      assessmentScoresWithCandidateSummary
    }
  }

  def findAssessmentScoresWithCandidateSummaryByEventId(eventId: UniqueIdentifier)
  : Future[List[AssessmentScoresFindResponse]] = {
    (for {
      event <- eventsRepository.getEvent(eventId.toString())
      candidateAllocations <- candidateAllocationRepository.allocationsForEvent(eventId.toString())
    } yield {
      candidateAllocations.foldLeft(Future.successful[List[AssessmentScoresFindResponse]](Nil)) {
        (listFuture, candidateAllocation) => listFuture.flatMap { list => {
          findOneAssessmentScoresWithCandidateSummaryByApplicationId(
            UniqueIdentifier(candidateAllocation.id),
            event,
            UniqueIdentifier(candidateAllocation.sessionId))
          }.map(_ :: list)
        }
      } map (_.reverse)
    }).flatMap(identity)
  }

  private def findOneAssessmentScoresWithCandidateSummaryByApplicationId(applicationId: UniqueIdentifier,
                                                                         event: Event,
                                                                         sessionId: UniqueIdentifier) = {
    val personalDetailsFut = personalDetailsRepository.find(applicationId.toString())
    val assessmentScoresFut = assessmentScoresRepository.find(applicationId)

    for {
      personalDetails <- personalDetailsFut
      assessmentScores <- assessmentScoresFut
    } yield {
      AssessmentScoresFindResponse(RecordCandidateScores(
        applicationId,
        personalDetails.firstName,
        personalDetails.lastName,
        event.venue.description,
        event.date,
        sessionId),
        assessmentScores)
    }
  }
}
