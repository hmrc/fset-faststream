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

package services.fsacscores

import model.FSACScores.{ FSACAllExercisesScoresAndFeedback, FSACExerciseScoresAndFeedback }
import model.UniqueIdentifier
import model.command.FSACScoresCommands.AssessmentExercise.AssessmentExercise
import model.command.FSACScoresCommands.{ ApplicationScores, AssessmentExercise, RecordCandidateScores }
import org.joda.time.DateTime
import play.api.Logger
import repositories.events.EventsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.{ CandidateAllocationMongoRepository, FSACScoresMongoRepository, FSACScoresRepository }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FSACScoresService extends FSACScoresService {
  // TODO MIGUEL: Audit
  override val fsacScoresRepository: FSACScoresRepository = repositories.fsacScoresRepository
  override val personalDetailsRepository: PersonalDetailsRepository = repositories.personalDetailsRepository
  override val candidateAllocationRepository: CandidateAllocationMongoRepository = repositories.candidateAllocationRepository
  override val eventsRepository: EventsRepository = repositories.eventsRepository


  override def save(scores: FSACAllExercisesScoresAndFeedback): Future[Unit] = {
    val scoresWithSubmittedDate = scores.copy(
      analysisExercise = scores.analysisExercise.map(_.copy(submittedDate = Some(DateTime.now))),
      groupExercise = scores.groupExercise.map(_.copy(submittedDate = Some(DateTime.now))),
      leadershipExercise = scores.leadershipExercise.map(_.copy(submittedDate = Some(DateTime.now)))
    )
    fsacScoresRepository.save(scoresWithSubmittedDate)
  }

  override def saveExercise(applicationId: UniqueIdentifier,
                            exercise: AssessmentExercise,
                            exerciseScores: FSACExerciseScoresAndFeedback): Future[Unit] = {
    exercise match {
      case AssessmentExercise.analysis =>
        saveAnalysisExercise(applicationId, exerciseScores)
      case AssessmentExercise.group =>
        saveGroupExercise(applicationId, exerciseScores)
      case AssessmentExercise.leadership =>
        saveLeadershipExercise(applicationId, exerciseScores)

      case _ => throw new Exception
    }
  }


  override def saveAnalysisExercise(applicationId: UniqueIdentifier, exerciseScores: FSACExerciseScoresAndFeedback): Future[Unit] = {
    val updateAnalysisExercise = (allExercisesOld: FSACAllExercisesScoresAndFeedback, exerciseScores: FSACExerciseScoresAndFeedback) => {
      allExercisesOld.copy(analysisExercise = Some(exerciseScores))
    }
    saveExercise(applicationId, exerciseScores, updateAnalysisExercise)
  }

  override def saveGroupExercise(applicationId: UniqueIdentifier, exerciseScores: FSACExerciseScoresAndFeedback): Future[Unit] = {
    val updateGroupExercise = (allExercisesOld: FSACAllExercisesScoresAndFeedback, exerciseScores: FSACExerciseScoresAndFeedback) => {
      allExercisesOld.copy(groupExercise = Some(exerciseScores))
    }
    saveExercise(applicationId, exerciseScores, updateGroupExercise)
  }

  override def saveLeadershipExercise(applicationId: UniqueIdentifier, exerciseScores: FSACExerciseScoresAndFeedback): Future[Unit] = {
    val updateLeadershipExercise = (allExercisesOld: FSACAllExercisesScoresAndFeedback, exerciseScores: FSACExerciseScoresAndFeedback) => {
      allExercisesOld.copy(leadershipExercise = Some(exerciseScores))
    }
    saveExercise(applicationId, exerciseScores, updateLeadershipExercise)
  }

  private def saveExercise(applicationId: UniqueIdentifier,
                           exerciseScores: FSACExerciseScoresAndFeedback,
                           update: (FSACAllExercisesScoresAndFeedback, FSACExerciseScoresAndFeedback) => FSACAllExercisesScoresAndFeedback)
  : Future[Unit] = {
    for {
      allExercisesOldMaybe <- fsacScoresRepository.find(applicationId)
      allExercisesOld = allExercisesOldMaybe.getOrElse(FSACAllExercisesScoresAndFeedback(applicationId, None, None, None))
      allExercisesNew = update(allExercisesOld, exerciseScores)
      allExerciseNewWithSubmittedDate = allExercisesNew.copy(
        analysisExercise = allExercisesNew.analysisExercise.map(_.copy(submittedDate = Some(DateTime.now))),
        groupExercise = allExercisesNew.groupExercise.map(_.copy(submittedDate = Some(DateTime.now))),
        leadershipExercise = allExercisesNew.leadershipExercise.map(_.copy(submittedDate = Some(DateTime.now)))
      )
      _ <- fsacScoresRepository.save(allExercisesNew)
    } yield {
      ()
    }
  }

  def findFSACScoresWithCandidateSummary(applicationId: UniqueIdentifier): Future[ApplicationScores] = {
    val candidateAllocationsFut = candidateAllocationRepository.find(applicationId.toString())
    val personalDetailsFut = personalDetailsRepository.find(applicationId.toString())
    val fsacScoresFut = fsacScoresRepository.find(applicationId)

    for {
      candidateAllocations <- candidateAllocationsFut
      personalDetails <- personalDetailsFut
      fsacScores <- fsacScoresFut
      event <- eventsRepository.getEvent(candidateAllocations.head.eventId)
    } yield {
      ApplicationScores(RecordCandidateScores(
        personalDetails.firstName,
        personalDetails.lastName,
        // TODO MIGUEL: we are in the process of refactoring Event class, at the end it will case class Venue(key: VenueKey, name: String)
        // But it is case class Venue(name: String, description: String)
        // The implication is now we do event.venue.description, but once the merge is done, it should be event.venue.name
        event.venue.description,
        event.date),
        fsacScores)
    }
  }
}

trait FSACScoresService {
  val fsacScoresRepository: FSACScoresRepository
  val personalDetailsRepository: PersonalDetailsRepository
  val candidateAllocationRepository: CandidateAllocationMongoRepository
  val eventsRepository: EventsRepository

  def save(scores: FSACAllExercisesScoresAndFeedback): Future[Unit]

  def saveExercise(applicationId: UniqueIdentifier,
                            exercise: AssessmentExercise,
                            exerciseScores: FSACExerciseScoresAndFeedback): Future[Unit]

  def saveAnalysisExercise(applicationId: UniqueIdentifier, exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit]

  def saveGroupExercise(applicationId: UniqueIdentifier, exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit]

  def saveLeadershipExercise(applicationId: UniqueIdentifier, exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit]

  def findFSACScoresWithCandidateSummary(applicationId: UniqueIdentifier): Future[ApplicationScores]
}
