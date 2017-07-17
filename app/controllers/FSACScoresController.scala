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

package controllers

import controllers.FSACScoresController.fsacScoresRepo
import model.FSACScores.{ FSACAllExercisesScoresAndFeedback, FSACExerciseScoresAndFeedback }
import model.models.UniqueIdentifier
import repositories.FSACScoresRepository
import services.fsacscores.FSACScoresService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

object FSACScoresController extends FSACScoresController {
  val fsacScoresService: FSACScoresService = FSACScoresService
  val fsacScoresRepo: FSACScoresRepository = repositories.fsacScoresRepository

  override def updateAnalysisExercise(applicationId: UniqueIdentifier,
                                      exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback)
  : Future[Unit] = {
    fsacScoresService.updateAnalysisExercise(applicationId, exerciseScoresAndFeedback)
  }
  override def updateGroupExercise(applicationId: UniqueIdentifier,
                                   exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit] = {
    fsacScoresService.updateGroupExercise(applicationId, exerciseScoresAndFeedback)
  }
  override def updateLeadershipExercise(applicationId: UniqueIdentifier,
                                        exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit] = {
    fsacScoresService.updateLeadershipExercise(applicationId, exerciseScoresAndFeedback)
  }

  def find(applicationId: UniqueIdentifier): Future[Option[FSACAllExercisesScoresAndFeedback]] = {
    fsacScoresRepo.find(applicationId)
  }

  def findAll: Future[Map[UniqueIdentifier, FSACAllExercisesScoresAndFeedback]] = {
    fsacScoresRepo.findAll
  }
}

trait FSACScoresController extends BaseController {
  val fsacScoresService: FSACScoresService
  val fsacScoresRepo: FSACScoresRepository

  def updateAnalysisExercise(applicationId: UniqueIdentifier,
                             exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit]
  def updateGroupExercise(applicationId: UniqueIdentifier,
                          exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit]
  def updateLeadershipExercise(applicationId: UniqueIdentifier,
                               exerciseScoresAndFeedback: FSACExerciseScoresAndFeedback): Future[Unit]
  def find(applicationId: UniqueIdentifier): Future[Option[FSACAllExercisesScoresAndFeedback]]
  def findAll: Future[Map[UniqueIdentifier, FSACAllExercisesScoresAndFeedback]]
}
