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

package controllers

import model.{ ApplicationRoute, Candidate, SearchCandidate }
import model.Exceptions.{ ApplicationNotFound, ContactDetailsNotFound, PersonalDetailsNotFound }
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent }
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.search.SearchForApplicantService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SearchForApplicantsController extends SearchForApplicantsController {
  val appRepository = applicationRepository
  val psRepository = personalDetailsRepository
  val cdRepository = faststreamContactDetailsRepository
  val searchForApplicantService = SearchForApplicantService
}

trait SearchForApplicantsController extends BaseController {

  val appRepository: GeneralApplicationRepository
  val psRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val searchForApplicantService: SearchForApplicantService

  val MAX_RESULTS = 25

  def findById(userId: String, frameworkId: String): Action[AnyContent] = Action.async { implicit request =>

    appRepository.findByUserId(userId, frameworkId).flatMap { application =>
      psRepository.find(application.applicationId).flatMap { pd =>
        cdRepository.find(userId).map { cd =>
          Ok(Json.toJson(Candidate(userId, Some(application.applicationId), Option(application.testAccountId), None, Some(pd
            .firstName),
            Some(pd.lastName), Some(pd.preferredName), Some(pd.dateOfBirth), Some(cd.address), cd.postCode, None,
            Some(application.applicationRoute), Some(application.applicationStatus))))
        }.recover {
          case _: ContactDetailsNotFound => Ok(Json.toJson(Candidate(userId, Some(application.applicationId),
            Option(application.testAccountId), None, Some(pd.firstName),
            Some(pd.lastName), Some(pd.preferredName), Some(pd.dateOfBirth), None, None, None,
            Some(application.applicationRoute), Some(application.applicationStatus))))
        }
      }.recover {
        case _: PersonalDetailsNotFound =>
          Ok(Json.toJson(Candidate(userId, Some(application.applicationId), Option(application.testAccountId),
            None, None, None, None, None, None, None, None,
            Some(application.applicationRoute), Some(application.applicationStatus))))
      }
    }.recover {
      // when application is not found, the application route is set to Faststream for backward compatibility
      case _: ApplicationNotFound =>
        Ok(
          Json.toJson(
            Candidate(userId, None, None, None, None, None, None, None, None, None, None, Some(ApplicationRoute
              .Faststream), None))
        )
    }
  }

  def findByCriteria = Action.async(parse.json) { implicit request =>
    withJsonBody[SearchCandidate] { searchCandidate =>
      createResult(searchForApplicantService.findByCriteria(searchCandidate))
    }
  }

  private def createResult(answer: Future[List[Candidate]]) = answer.map {
    case lst if lst.size > MAX_RESULTS => EntityTooLarge
    case lst if lst.isEmpty => NotFound
    case lst => Ok(Json.toJson(lst))
  }
}
