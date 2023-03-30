/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.Exceptions.ContactDetailsNotFound
import model.exchange.CandidateToRemove
import model.persisted.ContactDetailsWithId
import model.{Candidate, SearchCandidate}
import org.joda.time.LocalDate
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SearchForApplicantService @Inject() (appRepository: GeneralApplicationRepository,
                                           cdRepository: ContactDetailsRepository,
                                           authProviderClient: AuthProviderClient
                                          )(implicit ec: ExecutionContext) {

  def findByCriteria(searchCandidate: SearchCandidate)(implicit hc: HeaderCarrier): Future[Seq[Candidate]] = searchCandidate match {
    case SearchCandidate(None, None, None, Some(postCode)) => searchByPostCode(postCode)

    case SearchCandidate(firstOrPreferredName, lastName, dateOfBirth, postCode) =>
      searchByAllNamesOrDobAndFilterPostCode(firstOrPreferredName, lastName, dateOfBirth, postCode)
  }

  def findCandidateByUserId(userId: String): Future[Option[CandidateToRemove]] = {
    (for {
      candidateOpt <- appRepository.findCandidateByUserId(userId)
      candidate = candidateOpt.getOrElse(throw new Exception("No candidate found"))
      progressTimestamps <- appRepository.getProgressStatusTimestamps(candidate.applicationId
        .getOrElse(throw new Exception("No application id found")))
    } yield {
      val progressStatuses = progressTimestamps.map {
        case (progressStatus, _) => progressStatus
      }
      Some(CandidateToRemove(candidate, progressStatuses))
    }).recover {
      case _: Exception => None
    }
  }

  private def searchByPostCode(postCode: String): Future[Seq[Candidate]] =
    cdRepository.findByPostCode(postCode).flatMap { cdList =>
      Future.sequence(cdList.map { cd =>
        appRepository.findCandidateByUserId(cd.userId).map(_.map { candidate =>
          candidate.copy(address = Some(cd.address), postCode = cd.postCode, email = Some(cd.email))
        }).recover {
          case _: ContactDetailsNotFound => None
        }
      })
    }.map(_.flatten)

  private def searchByAllNamesOrDobAndFilterPostCode(firstOrPreferredName: Option[String],
                                                     lastName: Option[String],
                                                     dateOfBirth: Option[java.time.LocalDate],
                                                     postCodeOpt: Option[String]
                                                    )(implicit hc: HeaderCarrier): Future[Seq[Candidate]] =
    for {
      contactDetailsFromPostcode <- postCodeOpt.map(cdRepository.findByPostCode).getOrElse(Future.successful(List.empty))
      candidates <- appRepository.findByCriteria(firstOrPreferredName, lastName, dateOfBirth, contactDetailsFromPostcode.map(_.userId).toList)
      authProviderResults <- searchAuthProviderByFirstAndLastName(firstOrPreferredName, lastName)
      combinedCandidates = candidates ++ authProviderResults.filter(
        authProviderCandidate => !candidates.exists(_.userId == authProviderCandidate.userId)
      )
      contactDetailsOfCandidates <- cdRepository.findByUserIds(candidates.map(_.userId))
    } yield for {
      candidate <- combinedCandidates
      contactDetailMap = contactDetailsOfCandidates.map(x => x.userId -> x).toMap
    } yield {
      contactDetailMap.get(candidate.userId).map { candidateContactDetails =>
        candidate.copy(
          email = Some(candidateContactDetails.email),
          address = Some(candidateContactDetails.address),
          postCode = candidateContactDetails.postCode
        )
      }.getOrElse(candidate)
    }

  private def searchAuthProviderByFirstAndLastName(firstNameOpt: Option[String],
                                                   lastNameOpt: Option[String])
                                                  (implicit hc: HeaderCarrier): Future[Seq[Candidate]] =
    (firstNameOpt, lastNameOpt) match {
      case (Some(firstName), Some(lastName)) => searchByFirstNameAndLastName(firstName, lastName)
      case (Some(firstName), None) => searchByFirstName(firstName)
      case (None, Some(lastName)) => searchByLastName(lastName)
      case (None, None) => Future.successful(List.empty)
    }

  private def searchByFirstNameAndLastName(firstName: String, lastName: String)
                                          (implicit hc: HeaderCarrier): Future[Seq[Candidate]] =
    for {
      results <- authProviderClient.findByFirstNameAndLastName(firstName, lastName, List("candidate"))
    } yield {
      results.map( exchangeCandidate =>
        convertCandidate(exchangeCandidate)
      )
    }

  private def searchByFirstName(firstName: String)(implicit hc: HeaderCarrier): Future[Seq[Candidate]] =
    for {
      results <- authProviderClient.findByFirstName(firstName, List("candidate"))
    } yield {
      results.map( exchangeCandidate =>
        convertCandidate(exchangeCandidate)
      )
    }

  private def searchByLastName(lastName: String)(implicit hc: HeaderCarrier): Future[Seq[Candidate]] =
    for {
      results <- authProviderClient.findByLastName(lastName, List("candidate"))
    } yield {
      results.map( exchangeCandidate =>
        convertCandidate(exchangeCandidate)
      )
    }

  private def convertCandidate(exchangeCandidate: connectors.ExchangeObjects.Candidate): Candidate =
    Candidate(
      userId = exchangeCandidate.userId,
      applicationId = None,
      testAccountId = None,
      email = Some(exchangeCandidate.email),
      firstName = Some(exchangeCandidate.firstName),
      lastName = Some(exchangeCandidate.lastName),
      preferredName = exchangeCandidate.preferredName,
      dateOfBirth = None,
      address = None,
      postCode = None,
      country = None,
      // In this level we cannot say if the candidate's application exist, so it set to None
      // If the application does not exist, the candidate is Faststream
      // otherwise applicationRoute is saved in application
      applicationRoute = None,
      applicationStatus = None
    )
}
