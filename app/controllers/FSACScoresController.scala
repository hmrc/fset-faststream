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

import model.FSACScores._
import model.UniqueIdentifier
import model.command.FSACScoresCommands.{ ApplicationScores, AssessmentExercise, ExerciseScoresAndFeedback }
import play.api.libs.json.Json
import play.api.libs.json._
import play.api.mvc.Action
import repositories.FSACScoresRepository
import services.fsacscores.FSACScoresService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FSACScoresController extends FSACScoresController {
  val service: FSACScoresService = FSACScoresService
  val repository: FSACScoresRepository = repositories.fsacScoresRepository
}

trait FSACScoresController extends BaseController {
  val service: FSACScoresService
  val repository: FSACScoresRepository

  def submit() = Action.async(parse.json) {
    implicit request =>
      withJsonBody[ExerciseScoresAndFeedback] { submitRequest =>
        val result = AssessmentExercise.withName(submitRequest.exercise) match {
          case AssessmentExercise.analysisExercise =>
            service.saveAnalysisExercise(submitRequest.applicationId, submitRequest.scoresAndFeedback)
          case AssessmentExercise.groupExercise =>
            service.saveGroupExercise(submitRequest.applicationId, submitRequest.scoresAndFeedback)
          case AssessmentExercise.leadershipExercise =>
            service.saveLeadershipExercise(submitRequest.applicationId, submitRequest.scoresAndFeedback)
          case _ => throw new Exception
        }
        result.map(_ => Ok)
      }
  }

  def save() = Action.async(parse.json) {
    implicit request =>
      withJsonBody[FSACAllExercisesScoresAndFeedback] { scores =>
        service.save(scores).map(_ => Ok)
      }
  }

  /*
  def saveAnalysisExercise(applicationId: UniqueIdentifier) = Action.async(parse.json) {
    implicit request => withJsonBody[FSACExerciseScoresAndFeedback] { scores =>
      service.saveAnalysisExercise(applicationId, scores).map(_ => Ok)
    }
  }

  def saveGroupExercise(applicationId: UniqueIdentifier) = Action.async(parse.json) {
    implicit request => withJsonBody[FSACExerciseScoresAndFeedback] { scores =>
      service.saveGroupExercise(applicationId, scores).map(_ => Ok)
    }
  }

  def saveLeadershipExercise(applicationId: UniqueIdentifier) = Action.async(parse.json) {
    implicit request => withJsonBody[FSACExerciseScoresAndFeedback] { scores =>
      service.saveLeadershipExercise(applicationId, scores).map(_ => Ok)
    }
  }
*/

  def find(applicationId: UniqueIdentifier) = Action.async { implicit request =>
    repository.find(applicationId).map { scores =>
      Ok(Json.toJson(scores))
    }
  }

  def findAll = Action.async { implicit request =>
    repository.findAll.map { scores =>
      Ok(Json.toJson(scores))
    }
  }


  def findFSACScoresWithCandidateSummary(applicationId: UniqueIdentifier) = Action.async { implicit request =>
    service.findFSACScoresWithCandidateSummary(applicationId).map { scores =>
      Ok(Json.toJson(scores))
    }.recover {
      case _: Exception => NotFound
    }
  }
}
