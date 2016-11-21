/*
 * Copyright 2016 HM Revenue & Customs
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

import model.CandidateScoresCommands.Implicits._
import model.CandidateScoresCommands.{ ApplicationScores, CandidateScoresAndFeedback, RecordCandidateScores }
import play.api.libs.json.Json
import play.api.mvc.Action
import model.ApplicationStatus._
import repositories.application.GeneralApplicationRepository
import repositories.{ ApplicationAssessmentRepository, ApplicationAssessmentScoresRepository, PersonalDetailsRepository }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CandidateScoresController extends CandidateScoresController {

  import repositories._

  val aaRepository = applicationAssessmentRepository
  val pRepository = personalDetailsRepository
  val aasRepository = applicationAssessmentScoresRepository
  val aRepository = applicationRepository
}

trait CandidateScoresController extends BaseController {
  val aaRepository: ApplicationAssessmentRepository
  val pRepository: PersonalDetailsRepository
  val aasRepository: ApplicationAssessmentScoresRepository
  val aRepository: GeneralApplicationRepository

  def getCandidateScores(applicationId: String) = Action.async { implicit request =>
    val assessment = aaRepository.find(applicationId)
    val candidate = pRepository.find(applicationId)
    val applicationScores = aasRepository.tryFind(applicationId)

    for {
      a <- assessment
      c <- candidate
      as <- applicationScores
    } yield {
      Ok(Json.toJson(ApplicationScores(RecordCandidateScores(c.firstName, c.lastName, a.venue, a.date), as)))
    }
  }

  def createCandidateScoresAndFeedback(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[CandidateScoresAndFeedback] { candidateScoresAndFeedback =>
      candidateScoresAndFeedback.attendancy match {
        case Some(attendancy) =>
          val newStatus = if (attendancy) ASSESSMENT_SCORES_ENTERED else FAILED_TO_ATTEND
          for {
            _ <- aasRepository.save(candidateScoresAndFeedback)
            _ <- aRepository.updateStatus(applicationId, newStatus)
          } yield {
            Created
          }
        case _ =>
          Future.successful {
            BadRequest("attendancy is mandatory")
          }
      }
    }
  }

  def acceptCandidateScoresAndFeedback(applicationId: String) = Action.async { implicit request =>

    aRepository.updateStatus(applicationId, ASSESSMENT_SCORES_ACCEPTED).map(_ =>
      Ok(""))
  }
}
