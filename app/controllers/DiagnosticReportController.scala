/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.stream.scaladsl.Source
import connectors.AuthProviderClient
import connectors.exchange.AssessorDiagnosticReport
import javax.inject.{ Inject, Singleton }
import model.Exceptions.NotFoundException
import model.UniqueIdentifier
import play.api.libs.json.{ JsObject, Json }
import play.api.libs.streams.Streams
import play.api.mvc.{ Action, AnyContent }
import repositories._
import repositories.application.DiagnosticReportingRepository
import repositories.events.EventsRepository
import services.assessor.AssessorService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
/*
object DiagnosticReportController extends DiagnosticReportController {
  val drRepository: DiagnosticReportingRepository = diagnosticReportRepository
  val assessorAssessmentCentreScoresRepo: AssessorAssessmentScoresMongoRepository = repositories.assessorAssessmentScoresRepository
  val reviewerAssessmentCentreScoresRepo: ReviewerAssessmentScoresMongoRepository = repositories.reviewerAssessmentScoresRepository
  val eventsRepo: EventsMongoRepository = repositories.eventsRepository
//  val authProvider: AuthProviderClient = AuthProviderClient //TODO:fix
  val assessorService: AssessorService = AssessorService
}*/

@Singleton
class DiagnosticReportController @Inject() (drRepository: DiagnosticReportingRepository,
                                            assessorAssessmentCentreScoresRepo: AssessorAssessmentScoresMongoRepository,
                                            reviewerAssessmentCentreScoresRepo: ReviewerAssessmentScoresMongoRepository,
                                            eventsRepo: EventsRepository,
                                            authProvider: AuthProviderClient,
                                            assessorService: AssessorService
                                           ) extends BaseController {

//  def drRepository: DiagnosticReportingRepository
//  def assessorAssessmentCentreScoresRepo: AssessmentScoresMongoRepository
//  def reviewerAssessmentCentreScoresRepo: AssessmentScoresMongoRepository
//  def eventsRepo: EventsMongoRepository
//  def authProvider: AuthProviderClient
//  def assessorService: AssessorService

  def getApplicationByUserId(applicationId: String): Action[AnyContent] = Action.async { implicit request =>

    (for {
      application <- drRepository.findByApplicationId(applicationId)
      assessorScores <- assessorAssessmentCentreScoresRepo.find(UniqueIdentifier(applicationId))
      reviewerScores <- reviewerAssessmentCentreScoresRepo.find(UniqueIdentifier(applicationId))
    } yield {
      val assessorScoresJson = assessorScores.map(s => JsObject(Map("assessorScores" -> Json.toJson(s).as[JsObject])))
      val reviewerScoresJson = reviewerScores.map(s => JsObject(Map("reviewerScores" -> Json.toJson(s).as[JsObject])))

      val allJson = Seq(assessorScoresJson, reviewerScoresJson).flatten.foldLeft(application) { (a, v) =>
        a :+ v
      }

      Ok(Json.toJson(allJson))
    }).recover {
      case _ => NotFound
    }
  }

  def getAssessorDiagnosticDetail(userId: String): Action[AnyContent] = Action.async { implicit request =>
    authProvider.findByUserIds(Seq(userId)).flatMap { users =>
      users.headOption.map { user =>
        assessorService.findAssessor(userId).flatMap { assessor =>
          assessorService.findAssessorAllocations(userId).map { allocations =>
              AssessorDiagnosticReport(
                user.userId,
                user.roles,
                assessor,
                allocations
              )
          }
        }
      }.getOrElse(throw new NotFoundException(s"User with id $userId not found."))
    }.map( report => Ok(Json.toJson(report)))
  }

  def getAllApplications = Action { implicit request =>
    val response = Source.fromPublisher(Streams.enumeratorToPublisher(drRepository.findAll()))
    Ok.chunked(response)
  }

  def getAllEvents = Action { implicit request =>
    val response = Source.fromPublisher(Streams.enumeratorToPublisher(eventsRepo.findAllForExtract()))
    Ok.chunked(response)
  }
}
