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

package services.search

import model.Commands.{ Candidate, SearchCandidate }
import model.Exceptions.{ ApplicationNotFound, ContactDetailsNotFound, PersonalDetailsNotFound }
import model.PersistedObjects.ContactDetailsWithId
import org.joda.time.LocalDate
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object SearchForApplicantService extends SearchForApplicantService {
  val appRepository = applicationRepository
  val psRepository = personalDetailsRepository
  val cdRepository = contactDetailsRepository
}

trait SearchForApplicantService {

  val appRepository: GeneralApplicationRepository
  val psRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository

  def findByCriteria(searchCandidate: SearchCandidate): Future[List[Candidate]] = searchCandidate match {
    case SearchCandidate(None, None, None, Some(postCode)) => searchByPostCode(postCode)

    case SearchCandidate(firstOrPreferredName, lastName, dateOfBirth, postCode) =>
      searchByAllNamesOrDobAndFilterPostCode(firstOrPreferredName, lastName, dateOfBirth, postCode)

    case _ => Future(List.empty)
  }


  private def searchByPostCode(postCode: String) = {
    cdRepository.findByPostCode(postCode).flatMap { cdList =>
      Future.sequence(cdList.map { cd =>
        appRepository.findCandidateByUserId(cd.userId).map(_.map { candidate =>
          candidate.copy(address = Some(cd.address), postCode = Some(cd.postCode))
        }).recover {
          case e: ContactDetailsNotFound => None
        }
      })
    }.map(_.flatten)
  }

  private def searchByAllNamesOrDobAndFilterPostCode(firstOrPreferredName: Option[String],
                                                     lastName: Option[String],
                                                     dateOfBirth: Option[LocalDate],
                                                     postCodeOpt: Option[String]
                                                    ) = {

    val contactDetailsFromPostCode = postCodeOpt.map(cdRepository.findByPostCode).getOrElse(Future(List.empty))

    for {
      contactDetails <- contactDetailsFromPostCode
      candidates <- appRepository.findByCriteria(firstOrPreferredName, lastName, dateOfBirth, contactDetails.map(_.userId))
      contactDetailsOfCandidates <- cdRepository.findByUserIds(candidates.map(_.userId))
    } yield for {
      candidate <- candidates
      contactDetailMap = contactDetailsOfCandidates.map(x => x.userId -> x)(collection.breakOut): Map[String, ContactDetailsWithId]
    } yield {
      val candidateContactDetails = contactDetailMap(candidate.userId)

      candidate.copy(
        email = Some(candidateContactDetails.email),
        address = Some(candidateContactDetails.address),
        postCode = Some(candidateContactDetails.postCode)
      )
    }
  }
}
