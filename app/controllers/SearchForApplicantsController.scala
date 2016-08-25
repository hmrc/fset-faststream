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

import model.Commands._
import model.Exceptions.{ ApplicationNotFound, ContactDetailsNotFound, PersonalDetailsNotFound }
import org.joda.time.LocalDate
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SearchForApplicantsController extends SearchForApplicantsController {
  val appRepository = applicationRepository
  val psRepository = personalDetailsRepository
  val cdRepository = contactDetailsRepository
}

trait SearchForApplicantsController extends BaseController {

  import Implicits._

  val appRepository: GeneralApplicationRepository
  val psRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository

  val MAX_RESULTS = 10

  def findById(userId: String, frameworkId: String) = Action.async { implicit request =>

    appRepository.findByUserId(userId, frameworkId).flatMap { application =>
      psRepository.find(application.applicationId).flatMap { pd =>
        cdRepository.find(userId).map { cd =>
          Ok(Json.toJson(Candidate(userId, Some(application.applicationId), None, Some(pd.firstName),
            Some(pd.lastName), Some(pd.dateOfBirth), Some(cd.address), Some(cd.postCode), None)))
        }.recover {
          case e: ContactDetailsNotFound => Ok(Json.toJson(Candidate(userId, Some(application.applicationId), None, Some(pd.firstName),
            Some(pd.lastName), Some(pd.dateOfBirth), None, None, None)))
        }
      }.recover {
        case e: PersonalDetailsNotFound =>
          Ok(Json.toJson(Candidate(userId, Some(application.applicationId), None, None, None, None, None, None, None)))
      }
    }.recover {
      case e: ApplicationNotFound => Ok(Json.toJson(Candidate(userId, None, None, None, None, None, None, None, None)))
    }
  }

  def findByCriteria = Action.async(parse.json) { implicit request =>
    withJsonBody[SearchCandidate] {

      case SearchCandidate(None, None, Some(postCode)) =>
        searchByPostCode(postCode)
      case SearchCandidate(lastName, dateOfBirth, postCode) =>
        searchByLastNameOrDobAndFilterPostCode(lastName, dateOfBirth, postCode)
    }
  }

  private def searchByPostCode(postCode: String) = {
    createResult(
      cdRepository.findByPostCode(postCode).flatMap { cdList =>
        Future.sequence(cdList.map { cd =>
          appRepository.findCandidateByUserId(cd.userId).map(_.map { candidate =>
            candidate.copy(address = Some(cd.address), postCode = Some(cd.postCode))
          }).recover {
            case e: ContactDetailsNotFound => None
          }
        })
      }.map(_.flatten)
    )
  }

  private def searchByLastNameOrDobAndFilterPostCode(lastName: Option[String], dateOfBirth: Option[LocalDate], postCode: Option[String]) = {
    appRepository.findByCriteria(lastName, dateOfBirth) flatMap { candidateList =>
      val answer = Future.sequence(candidateList.map { candidate =>
        cdRepository.find(candidate.userId).map { cd =>
          candidate.copy(address = Some(cd.address), postCode = Some(cd.postCode))
        }.recover {
          case e: ContactDetailsNotFound => candidate
        }
      })

      postCode match {
        case Some(pc) =>
          val result = answer.map(lst => lst.filter(c => c.postCode.map(_ == pc).getOrElse(true)))
          createResult(result)
        case None => createResult(answer)
      }
    }
  }

  private def createResult(answer: Future[List[Candidate]]) = answer.map {
    case lst if lst.size > MAX_RESULTS => EntityTooLarge
    case lst if lst.isEmpty => NotFound
    case lst => Ok(Json.toJson(lst))
  }
}
