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
import model.models.UniqueIdentifier
import play.api.Logger
import repositories.{ FSACScoresMongoRepository, FSACScoresRepository }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FSACScoresService extends FSACScoresService {
  override val repository: FSACScoresRepository = repositories.fsacScoresRepository

  override def updateAnalysisExercise(applicationId: UniqueIdentifier, exerciseScores: FSACExerciseScoresAndFeedback) = {
    val updateAnalysisExercise = (allExercisesOld: FSACAllExercisesScoresAndFeedback, exerciseScores: FSACExerciseScoresAndFeedback) => {
      allExercisesOld.copy(analysisExercise = Some(exerciseScores))
    }
    updateExercise(applicationId, exerciseScores, updateAnalysisExercise)
  }

  override def updateGroupExercise(applicationId: UniqueIdentifier, exerciseScores: FSACExerciseScoresAndFeedback) = {
    val updateGroupExercise = (allExercisesOld: FSACAllExercisesScoresAndFeedback, exerciseScores: FSACExerciseScoresAndFeedback) => {
      allExercisesOld.copy(groupExercise = Some(exerciseScores))
    }
    updateExercise(applicationId, exerciseScores, updateGroupExercise)
  }

  override def updateLeadershipExercise(applicationId: UniqueIdentifier, exerciseScores: FSACExerciseScoresAndFeedback) = {
    val updateLeadershipExercise = (allExercisesOld: FSACAllExercisesScoresAndFeedback, exerciseScores: FSACExerciseScoresAndFeedback) => {
      allExercisesOld.copy(leadershipExercise = Some(exerciseScores))
    }
    updateExercise(applicationId, exerciseScores, updateLeadershipExercise)
  }

  private def updateExercise(applicationId: UniqueIdentifier,
                             exerciseScores: FSACExerciseScoresAndFeedback,
                             update: (FSACAllExercisesScoresAndFeedback, FSACExerciseScoresAndFeedback) => FSACAllExercisesScoresAndFeedback) = {
    for {
      allExercisesOldMaybe <- repository.find(applicationId)
      allExercisesOld = allExercisesOldMaybe.getOrElse(FSACAllExercisesScoresAndFeedback(applicationId, None, None, None))
      allExercisesNew = update(allExercisesOld, exerciseScores)
      _ <-repository.update(allExercisesNew)
    } yield {
      ()
    }
  }
}

trait FSACScoresService {
  val repository: FSACScoresRepository

  def updateAnalysisExercise(applicationId: UniqueIdentifier, exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback)
  def updateGroupExercise(applicationId: UniqueIdentifier, exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback)
  def updateLeadershipExercise(applicationId: UniqueIdentifier, exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback)

}
