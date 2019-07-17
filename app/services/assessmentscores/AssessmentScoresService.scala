/*
 * Copyright 2019 HM Revenue & Customs
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
import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresExercise, AssessmentScoresFinalFeedback }
import model.{ ProgressStatuses, UniqueIdentifier }
import model.command.AssessmentScoresCommands.{ AssessmentScoresSectionType, AssessmentScoresFindResponse, AssessmentScoresCandidateSummary }
import model.persisted.eventschedules.Event
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.events.EventsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.{ AssessmentScoresRepository, CandidateAllocationMongoRepository }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

trait AssessmentScoresService {
  val applicationRepository: GeneralApplicationRepository
  val assessmentScoresRepository: AssessmentScoresRepository
  val candidateAllocationRepository: CandidateAllocationMongoRepository
  val eventsRepository: EventsRepository
  val personalDetailsRepository: PersonalDetailsRepository

  val dateTimeFactory: DateTimeFactory
  val statusToUpdateTheApplicationTo: ProgressStatuses.ProgressStatus

  def save(scores: AssessmentScoresAllExercises)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val scoresWithSubmittedDate = scores.copy(
      analysisExercise = scores.analysisExercise.map(_.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone))),
      groupExercise = scores.groupExercise.map(_.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone))),
      leadershipExercise = scores.leadershipExercise.map(_.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone))),
      finalFeedback = scores.finalFeedback.map(_.copy(acceptedDate = dateTimeFactory.nowLocalTimeZone))
    )
    for {
      _ <- assessmentScoresRepository.save(scoresWithSubmittedDate)
      _ <- updateStatusIfNeeded(scores)
    } yield {
      ()
    }
  }

  def saveExercise(applicationId: UniqueIdentifier,
    assessmentExerciseType: AssessmentScoresSectionType.AssessmentScoresSectionType,
    newExerciseScores: AssessmentScoresExercise): Future[Unit] = {
    saveOrSubmitExercise(applicationId, assessmentExerciseType, newExerciseScores.copy(savedDate = Some(dateTimeFactory.nowLocalTimeZone)))
  }

  def submitExercise(applicationId: UniqueIdentifier,
    assessmentExerciseType: AssessmentScoresSectionType.AssessmentScoresSectionType,
    newExerciseScores: AssessmentScoresExercise): Future[Unit] = {
    saveOrSubmitExercise(applicationId, assessmentExerciseType, newExerciseScores.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone)))
  }

  private def saveOrSubmitExercise(applicationId: UniqueIdentifier,
    assessmentExerciseType: AssessmentScoresSectionType.AssessmentScoresSectionType,
    newExerciseScores: AssessmentScoresExercise): Future[Unit] = {
    def updateAllExercisesWithExercise(oldAllExercisesScores: AssessmentScoresAllExercises,
      newExerciseScoresWithSubmittedDate: AssessmentScoresExercise) = {
      assessmentExerciseType match {
        case AssessmentScoresSectionType.analysisExercise =>
          oldAllExercisesScores.copy(analysisExercise = Some(newExerciseScoresWithSubmittedDate))
        case AssessmentScoresSectionType.groupExercise =>
          oldAllExercisesScores.copy(groupExercise = Some(newExerciseScoresWithSubmittedDate))
        case AssessmentScoresSectionType.leadershipExercise =>
          oldAllExercisesScores.copy(leadershipExercise = Some(newExerciseScoresWithSubmittedDate))
      }
    }

    for {
      oldAllExercisesScoresMaybe <- assessmentScoresRepository.find(applicationId)
      oldAllExercisesScores = oldAllExercisesScoresMaybe.getOrElse(AssessmentScoresAllExercises(applicationId, None, None, None))
      newAllExercisesScores = updateAllExercisesWithExercise(oldAllExercisesScores, newExerciseScores)
      _ <- assessmentScoresRepository.saveExercise(applicationId, assessmentExerciseType, newExerciseScores)
      _ <- updateStatusIfNeeded(newAllExercisesScores)
    } yield {
      ()
    }
  }

  // We only submit final feedback for Reviewers/ QAC
  // newFinalFeedback.analysisExercise/groupExercise/leadershipExercise should be defined.
  def submitFinalFeedback(applicationId: UniqueIdentifier, newFinalFeedback: AssessmentScoresFinalFeedback): Future[Unit] = {
    def findScoresAndVerify(applicationId: UniqueIdentifier) = {
      assessmentScoresRepository.find(applicationId).map { scoresOpt =>
        require(scoresOpt.exists { scores =>
          scores.analysisExercise.isDefined && scores.groupExercise.isDefined && scores.leadershipExercise.isDefined
        })
        scoresOpt
      }
    }

    def buildNewAllExercisesScoresWithSubmittedDate(oldAllExercisesScoresMaybe: Option[AssessmentScoresAllExercises]) = {
      val newSubmittedDate = dateTimeFactory.nowLocalTimeZone
      val newFinalFeedbackWithSubmittedDate = newFinalFeedback.copy(acceptedDate = newSubmittedDate)

      val oldAllExercisesScores = oldAllExercisesScoresMaybe.getOrElse(AssessmentScoresAllExercises(applicationId, None, None, None))
      val oldAnalysisExerciseWithSubmittedDate = oldAllExercisesScores.analysisExercise.map(_.copy(submittedDate = Some(newSubmittedDate)))
      val oldGroupExerciseWithSubmittedDate = oldAllExercisesScores.groupExercise.map(_.copy(submittedDate = Some(newSubmittedDate)))
      val oldLeadershipExerciseWithSubmittedDate = oldAllExercisesScores.leadershipExercise.map(_.copy(submittedDate = Some(newSubmittedDate)))

      oldAllExercisesScores.copy(
        analysisExercise = oldAnalysisExerciseWithSubmittedDate,
        groupExercise = oldGroupExerciseWithSubmittedDate,
        leadershipExercise = oldLeadershipExerciseWithSubmittedDate,
        finalFeedback = Some(newFinalFeedbackWithSubmittedDate))
    }

    for {
      oldAllExercisesScoresMaybe <- findScoresAndVerify(applicationId)
      newAllExercisesScoresWithSubmittedDate = buildNewAllExercisesScoresWithSubmittedDate(oldAllExercisesScoresMaybe)
      _ <- assessmentScoresRepository.save(newAllExercisesScoresWithSubmittedDate)
      _ <- updateStatusIfNeeded(newAllExercisesScoresWithSubmittedDate)
    } yield {
      ()
    }
  }

  protected def shouldUpdateStatus(allExercisesScores: AssessmentScoresAllExercises): Boolean

  protected def updateStatusIfNeeded(allExercisesScores: AssessmentScoresAllExercises): Future[Unit] = {
    if (shouldUpdateStatus(allExercisesScores)) {
      applicationRepository.addProgressStatusAndUpdateAppStatus(
        allExercisesScores.applicationId.toString(),
        statusToUpdateTheApplicationTo)
    } else {
      Future.successful(())
    }
  }

  def findAssessmentScoresWithCandidateSummaryByApplicationId(applicationId: UniqueIdentifier): Future[AssessmentScoresFindResponse] = {
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

  def findAcceptedAssessmentScoresAndFeedbackByApplicationId(applicationId: UniqueIdentifier): Future[Option[AssessmentScoresAllExercises]] = {
    for {
      assessmentScoresAndFeedback <- assessmentScoresRepository.findAccepted(applicationId)
    } yield {
      assessmentScoresAndFeedback
    }
  }

  def findAssessmentScoresWithCandidateSummaryByEventId(eventId: UniqueIdentifier)
  : Future[List[AssessmentScoresFindResponse]] = {
    (for {
      event <- eventsRepository.getEvent(eventId.toString())
      candidateAllocations <- candidateAllocationRepository.activeAllocationsForEvent(eventId.toString())
    } yield {
      candidateAllocations.foldLeft(Future.successful[List[AssessmentScoresFindResponse]](Nil)) {
        (listFuture, candidateAllocation) =>
          listFuture.flatMap { list => {
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
      AssessmentScoresFindResponse(AssessmentScoresCandidateSummary(
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

abstract class AssessorAssessmentScoresService extends AssessmentScoresService {
  override val statusToUpdateTheApplicationTo = ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ENTERED

  override def shouldUpdateStatus(allExercisesScores: AssessmentScoresAllExercises): Boolean = {
    allExercisesScores.analysisExercise.exists(_.isSubmitted) &&
      allExercisesScores.groupExercise.exists(_.isSubmitted) &&
      allExercisesScores.leadershipExercise.exists(_.isSubmitted)
  }
}

object AssessorAssessmentScoresService extends AssessorAssessmentScoresService {
  override val applicationRepository: GeneralApplicationRepository = repositories.applicationRepository
  override val assessmentScoresRepository: AssessmentScoresRepository = repositories.assessorAssessmentScoresRepository
  override val candidateAllocationRepository: CandidateAllocationMongoRepository = repositories.candidateAllocationRepository
  override val eventsRepository: EventsRepository = repositories.eventsRepository
  override val personalDetailsRepository: PersonalDetailsRepository = repositories.personalDetailsRepository

  override val dateTimeFactory = DateTimeFactory
}

abstract class ReviewerAssessmentScoresService extends AssessmentScoresService {
  override val statusToUpdateTheApplicationTo = ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED

  override def shouldUpdateStatus(allExercisesScores: AssessmentScoresAllExercises): Boolean = {
    allExercisesScores.analysisExercise.exists(_.isSubmitted) &&
      allExercisesScores.groupExercise.exists(_.isSubmitted) &&
      allExercisesScores.leadershipExercise.exists(_.isSubmitted) &&
      allExercisesScores.finalFeedback.isDefined
  }
}

object ReviewerAssessmentScoresService extends ReviewerAssessmentScoresService {
  override val applicationRepository: GeneralApplicationRepository = repositories.applicationRepository
  override val assessmentScoresRepository: AssessmentScoresRepository = repositories.reviewerAssessmentScoresRepository
  override val candidateAllocationRepository: CandidateAllocationMongoRepository = repositories.candidateAllocationRepository
  override val eventsRepository: EventsRepository = repositories.eventsRepository
  override val personalDetailsRepository: PersonalDetailsRepository = repositories.personalDetailsRepository

  override val dateTimeFactory = DateTimeFactory
}
