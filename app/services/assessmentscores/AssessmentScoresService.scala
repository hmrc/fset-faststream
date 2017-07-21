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
import model.command.AssessmentScoresCommands.AssessmentExerciseType.AssessmentExerciseType
import model.command.AssessmentScoresCommands.{ AssessmentExerciseType, AssessmentScoresFindResponse, RecordCandidateScores }
import play.api.mvc.RequestHeader
import repositories.events.EventsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.{ AssessmentScoresRepository, CandidateAllocationMongoRepository }
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AssessmentScoresService extends AssessmentScoresService {
  private val AssessmentScoresAllExercisesSaved = "AssessmentScoresAllExercisesSaved"
  private val AssessmentScoresOneExerciseSubmitted = "AssessmentScoresOneExerciseSubmitted"

  override val auditService = AuditService

  override val assessmentScoresRepository: AssessmentScoresRepository = repositories.assessmentScoresRepository
  override val candidateAllocationRepository: CandidateAllocationMongoRepository = repositories.candidateAllocationRepository
  override val eventsRepository: EventsRepository = repositories.eventsRepository
  override val personalDetailsRepository: PersonalDetailsRepository = repositories.personalDetailsRepository

  override val dateTimeFactory = DateTimeFactory

  override def save(scores: AssessmentScoresAllExercises)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val scoresWithSubmittedDate = scores.copy(
      analysisExercise = scores.analysisExercise.map(_.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone))),
      groupExercise = scores.groupExercise.map(_.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone))),
      leadershipExercise = scores.leadershipExercise.map(_.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone)))
    )
    assessmentScoresRepository.save(scoresWithSubmittedDate).map { _ =>
      val details = Map(
        "applicationId" -> scores.applicationId.toString(),
        "assessorId" -> scores.analysisExercise.map(_.updatedBy.toString).getOrElse("Unknown")
      )
      auditService.logEvent(AssessmentScoresAllExercisesSaved, details)
    }
  }

  override def saveExercise(applicationId: UniqueIdentifier,
                            assessmentExerciseType: AssessmentExerciseType,
                            newExerciseScores: AssessmentScoresExercise)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
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
      auditDetails = Map(
        "applicationId" -> applicationId.toString(),
        "exercise" -> assessmentExerciseType.toString,
        "assessorId" -> newExerciseScores.updatedBy.toString())
      _ <- Future.successful(auditService.logEvent(AssessmentScoresOneExerciseSubmitted, auditDetails))
    } yield {
      ()
    })
  }

  def findAssessmentScoresWithCandidateSummary(applicationId: UniqueIdentifier): Future[AssessmentScoresFindResponse] = {
    val candidateAllocationsFut = candidateAllocationRepository.find(applicationId.toString())
    val personalDetailsFut = personalDetailsRepository.find(applicationId.toString())
    val assessmentScoresFut = assessmentScoresRepository.find(applicationId)

    for {
      candidateAllocations <- candidateAllocationsFut
      personalDetails <- personalDetailsFut
      assessmentScores <- assessmentScoresFut
      event <- eventsRepository.getEvent(candidateAllocations.head.eventId)
    } yield {
      AssessmentScoresFindResponse(RecordCandidateScores(
        personalDetails.firstName,
        personalDetails.lastName,
        // TODO MIGUEL: we are in the process of refactoring Event class, at the end it will case class Venue(key: VenueKey, name: String)
        // But it is case class Venue(name: String, description: String)
        // The implication is now we do event.venue.description, but once the merge is done, it should be event.venue.name
        event.venue.description,
        event.date),
        assessmentScores)
    }
  }
}

trait AssessmentScoresService {
  val auditService: AuditService

  val assessmentScoresRepository: AssessmentScoresRepository
  val candidateAllocationRepository: CandidateAllocationMongoRepository
  val eventsRepository: EventsRepository
  val personalDetailsRepository: PersonalDetailsRepository

  val dateTimeFactory: DateTimeFactory

  def save(scores: AssessmentScoresAllExercises)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]

  def saveExercise(applicationId: UniqueIdentifier,
                   exercise: AssessmentExerciseType,
                   exerciseScores: AssessmentScoresExercise)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]

  def findAssessmentScoresWithCandidateSummary(applicationId: UniqueIdentifier): Future[AssessmentScoresFindResponse]
}
