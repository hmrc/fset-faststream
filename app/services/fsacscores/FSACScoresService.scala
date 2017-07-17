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
import play.api.Logger
import repositories.{ FSACScoresMongoRepository, FSACScoresRepository }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FSACScoresService extends FSACScoresService {
  override val repository: FSACScoresRepository = repositories.fsacScoresRepository

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
                             save: (FSACAllExercisesScoresAndFeedback, FSACExerciseScoresAndFeedback) => FSACAllExercisesScoresAndFeedback)
  : Future[Unit] = {
    for {
      allExercisesOldMaybe <- repository.find(applicationId)
      allExercisesOld = allExercisesOldMaybe.getOrElse(FSACAllExercisesScoresAndFeedback(applicationId, None, None, None))
      allExercisesNew = save(allExercisesOld, exerciseScores)
      _ <-repository.save(allExercisesNew)
    } yield {
      ()
    }
  }
}

trait FSACScoresService {
  val repository: FSACScoresRepository

  def saveAnalysisExercise(applicationId: UniqueIdentifier, exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit]
  def saveGroupExercise(applicationId: UniqueIdentifier, exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit]
  def saveLeadershipExercise(applicationId: UniqueIdentifier, exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit]

}
