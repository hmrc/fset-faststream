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

import model.assessmentscores._
import model.UniqueIdentifier
import model.command.AssessmentScoresCommands.{ AssessmentScoresFindResponse, AssessmentExercise, AssessmentScoresSubmitRequest }
import play.api.libs.json.Json
import play.api.libs.json._
import play.api.mvc.Action
import repositories.AssessmentScoresRepository
import services.assessmentscores.AssessmentScoresService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentScoresController extends AssessmentScoresController {
  val service: AssessmentScoresService = AssessmentScoresService
  val repository: AssessmentScoresRepository = repositories.assessmentScoresRepository
}

trait AssessmentScoresController extends BaseController {
  val service: AssessmentScoresService
  val repository: AssessmentScoresRepository

  def submit() = Action.async(parse.json) {
    implicit request =>
      withJsonBody[AssessmentScoresSubmitRequest] { submitRequest =>
        service.saveExercise(
          submitRequest.applicationId,
          AssessmentExercise.withName(submitRequest.exercise),
          submitRequest.scoresAndFeedback
        ).map(_ => Ok)
      }
  }

  def save() = Action.async(parse.json) {
    implicit request =>
      withJsonBody[AssessmentScoresAllExercises] { scores =>
        service.save(scores).map(_ => Ok)
      }
  }

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


  def findAssessmentScoresWithCandidateSummary(applicationId: UniqueIdentifier) = Action.async { implicit request =>
    service.findAssessmentScoresWithCandidateSummary(applicationId).map { scores =>
      Ok(Json.toJson(scores))
    }.recover {
      case _: Exception => NotFound
    }
  }
}
