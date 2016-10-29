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

import connectors.AuthProviderClient
import model.ApplicationRoute.ApplicationRoute
import model.Commands.{ Candidate, SearchCandidate }
import model.Exceptions.{ ApplicationNotFound, ContactDetailsNotFound, PersonalDetailsNotFound }
import model.PersistedObjects.ContactDetailsWithId
import org.joda.time.LocalDate
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object SearchForApplicantService extends SearchForApplicantService {
  val appRepository = applicationRepository
  val psRepository = personalDetailsRepository
  val cdRepository = contactDetailsRepository
  val authProviderClient = AuthProviderClient
}

trait SearchForApplicantService {

  val appRepository: GeneralApplicationRepository
  val psRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val authProviderClient: AuthProviderClient

  def findByCriteria(searchCandidate: SearchCandidate)(implicit hc: HeaderCarrier): Future[List[Candidate]] = searchCandidate match {
    case SearchCandidate(applicationRoute, None, None, None, Some(postCode)) => searchByPostCode(applicationRoute, postCode)

    case SearchCandidate(applicationRoute, firstOrPreferredName, lastName, dateOfBirth, postCode) =>
      searchByAllNamesOrDobAndFilterPostCode(applicationRoute, firstOrPreferredName, lastName, dateOfBirth, postCode)
  }

  private def searchByPostCode(applicationRoute: ApplicationRoute, postCode: String): Future[List[Candidate]] = {
    cdRepository.findByPostCode(postCode).flatMap { cdList =>
      Future.sequence(cdList.map { cd =>
        appRepository.findCandidateByUserId(cd.userId).map(_.map { candidate =>
          candidate.copy(address = Some(cd.address), postCode = cd.postCode)
        }).recover {
          case e: ContactDetailsNotFound => None
        }
      })
    }.map(_.flatten)
  }

  private def searchByAllNamesOrDobAndFilterPostCode(applicationRoute: ApplicationRoute,
                                                     firstOrPreferredName: Option[String],
                                                     lastName: Option[String],
                                                     dateOfBirth: Option[LocalDate],
                                                     postCodeOpt: Option[String]
                                                    )(implicit hc: HeaderCarrier): Future[List[Candidate]] = {
    for {
      contactDetailsFromPostcode <- postCodeOpt.map(cdRepository.findByPostCode).getOrElse(Future.successful(List.empty))
      candidates <- appRepository.findByCriteria(firstOrPreferredName, lastName, dateOfBirth, contactDetailsFromPostcode.map(_.userId))
      authProviderResults <- searchAuthProviderByFirstAndLastName(firstOrPreferredName, lastName)
      combinedCandidates = candidates ++ authProviderResults.filter(
        authProviderCandidate => !candidates.exists(_.userId == authProviderCandidate.userId)
      )
      contactDetailsOfCandidates <- cdRepository.findByUserIds(candidates.map(_.userId))
    } yield for {
      candidate <- combinedCandidates
      contactDetailMap = contactDetailsOfCandidates.map(x => x.userId -> x)(collection.breakOut): Map[String, ContactDetailsWithId]
    } yield {
      contactDetailMap.get(candidate.userId).map { candidateContactDetails =>
        candidate.copy(
          email = Some(candidateContactDetails.email),
          address = Some(candidateContactDetails.address),
          postCode = candidateContactDetails.postCode
        )
      }.getOrElse(candidate)
    }
  }

  private def searchAuthProviderByFirstAndLastName(firstNameOpt: Option[String],
                                                   lastNameOpt: Option[String])(
                                                   implicit hc: HeaderCarrier): Future[List[Candidate]] = {

    val firstNameResultsFut = firstNameOpt.map {
      firstName => authProviderClient.findByFirstName(firstName, List("candidate"))
    }.getOrElse(Future.successful(List.empty))

    val lastNameResultsFut = lastNameOpt.map {
      lastName => authProviderClient.findByLastName(lastName, List("candidate"))
    }.getOrElse(Future.successful(List.empty))

    for {
      firstNameResults <- firstNameResultsFut
      lastNameResults <- lastNameResultsFut
    } yield {
      (firstNameResults ++ lastNameResults).distinct.map(exchangeCandidate =>
        Candidate(
          exchangeCandidate.userId,
          None,
          Some(exchangeCandidate.email),
          Some(exchangeCandidate.firstName),
          Some(exchangeCandidate.lastName),
          exchangeCandidate.preferredName,
          None,
          None,
          None,
          None
        )
      )
    }
  }
}
