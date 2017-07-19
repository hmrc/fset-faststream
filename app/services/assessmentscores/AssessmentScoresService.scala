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

import model.assessmentscores.{ AssessmentScoresAllExercises, AssessmentScoresExercise }
import model.UniqueIdentifier
import model.command.AssessmentScoresCommands.AssessmentExercise.AssessmentExercise
import model.command.AssessmentScoresCommands.{ AssessmentScoresFindResponse, AssessmentExercise, RecordCandidateScores }
import org.joda.time.DateTime
import repositories.events.EventsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.{ CandidateAllocationMongoRepository, AssessmentScoresMongoRepository, AssessmentScoresRepository }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AssessmentScoresService extends AssessmentScoresService {
  // TODO MIGUEL: Audit
  override val assessmentScoresRepository: AssessmentScoresRepository = repositories.assessmentScoresRepository
  override val personalDetailsRepository: PersonalDetailsRepository = repositories.personalDetailsRepository
  override val candidateAllocationRepository: CandidateAllocationMongoRepository = repositories.candidateAllocationRepository
  override val eventsRepository: EventsRepository = repositories.eventsRepository


  override def save(scores: AssessmentScoresAllExercises): Future[Unit] = {
    val scoresWithSubmittedDate = scores.copy(
      analysisExercise = scores.analysisExercise.map(_.copy(submittedDate = Some(DateTime.now))),
      groupExercise = scores.groupExercise.map(_.copy(submittedDate = Some(DateTime.now))),
      leadershipExercise = scores.leadershipExercise.map(_.copy(submittedDate = Some(DateTime.now)))
    )
    assessmentScoresRepository.save(scoresWithSubmittedDate)
  }

  override def saveExercise(applicationId: UniqueIdentifier,
                            exercise: AssessmentExercise,
                            exerciseScores: AssessmentScoresExercise): Future[Unit] = {
    val exerciseScoresWithSubmittedDate = exerciseScores.copy(submittedDate = Some(DateTime.now))

    exercise match {
      case AssessmentExercise.`analysisExercise` =>
        saveAnalysisExercise(applicationId, exerciseScoresWithSubmittedDate)
      case AssessmentExercise.`groupExercise` =>
        saveGroupExercise(applicationId, exerciseScoresWithSubmittedDate)
      case AssessmentExercise.`leadershipExercise` =>
        saveLeadershipExercise(applicationId, exerciseScoresWithSubmittedDate)
      case _ => throw new Exception // TODO MIGUEL
    }
  }


  private def saveAnalysisExercise(applicationId: UniqueIdentifier, exerciseScores: AssessmentScoresExercise): Future[Unit] = {
    val updateAnalysisExercise = (allExercisesOld: AssessmentScoresAllExercises, exerciseScores: AssessmentScoresExercise) => {
      allExercisesOld.copy(analysisExercise = Some(exerciseScores))
    }
    saveExercise(applicationId, exerciseScores, updateAnalysisExercise)
  }

  private def saveGroupExercise(applicationId: UniqueIdentifier, exerciseScores: AssessmentScoresExercise): Future[Unit] = {
    val updateGroupExercise = (allExercisesOld: AssessmentScoresAllExercises, exerciseScores: AssessmentScoresExercise) => {
      allExercisesOld.copy(groupExercise = Some(exerciseScores))
    }
    saveExercise(applicationId, exerciseScores, updateGroupExercise)
  }

  private def saveLeadershipExercise(applicationId: UniqueIdentifier, exerciseScores: AssessmentScoresExercise): Future[Unit] = {
    val updateLeadershipExercise = (allExercisesOld: AssessmentScoresAllExercises, exerciseScores: AssessmentScoresExercise) => {
      allExercisesOld.copy(leadershipExercise = Some(exerciseScores))
    }
    saveExercise(applicationId, exerciseScores, updateLeadershipExercise)
  }

  private def saveExercise(applicationId: UniqueIdentifier,
                           exerciseScores: AssessmentScoresExercise,
                           update: (AssessmentScoresAllExercises, AssessmentScoresExercise) => AssessmentScoresAllExercises)
  : Future[Unit] = {
    for {
      allExercisesOldMaybe <- assessmentScoresRepository.find(applicationId)
      allExercisesOld = allExercisesOldMaybe.getOrElse(AssessmentScoresAllExercises(applicationId, None, None, None))
      allExercisesNew = update(allExercisesOld, exerciseScores)
      _ <- assessmentScoresRepository.save(allExercisesNew)
    } yield {
      ()
    }
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
  val assessmentScoresRepository: AssessmentScoresRepository
  val personalDetailsRepository: PersonalDetailsRepository
  val candidateAllocationRepository: CandidateAllocationMongoRepository
  val eventsRepository: EventsRepository

  def save(scores: AssessmentScoresAllExercises): Future[Unit]

  def saveExercise(applicationId: UniqueIdentifier,
                            exercise: AssessmentExercise,
                            exerciseScores: AssessmentScoresExercise): Future[Unit]

  def findAssessmentScoresWithCandidateSummary(applicationId: UniqueIdentifier): Future[AssessmentScoresFindResponse]
}
