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

package services.reporting

import model.persisted.{ UserApplicationProfile, UserIdWithEmail }
import play.Logger
import repositories.application.ReportingRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.faststreamContactDetailsRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DuplicateCandidate(email: String, firstName: String, lastName: String, latestProgressStatus: String)

case class DuplicateApplicationGroup(matchType: Int, candidates: List[DuplicateCandidate])

object DuplicateDetectionService extends DuplicateDetectionService {
  val reportingRepository: ReportingRepository = repositories.reportingRepository
  val cdRepository: ContactDetailsRepository = faststreamContactDetailsRepository
}

trait DuplicateDetectionService {
  private val HighProbabilityMatchGroup = 1
  private val MediumProbabilityMatchGroup = 2

  val reportingRepository: ReportingRepository
  val cdRepository: ContactDetailsRepository

  def findAll: Future[List[DuplicateApplicationGroup]] = {
    def toUserIdToEmailMap(cds: List[UserIdWithEmail]) = {
      cds.groupBy(_.userId).mapValues(_.head.email)
    }

    for {
      allCandidates <- reportingRepository.candidatesForDuplicateDetectionReport
      candidatesEmails <- cdRepository.findEmails.map(toUserIdToEmailMap)
    } yield {
      val exportedApplications = allCandidates.filter(_.exportedToParity)
      Logger.info(s"Detect duplications from ${allCandidates.length} candidates")
      Logger.info(s"Detect duplications for ${exportedApplications.length} exported candidates")
      findDuplicates(exportedApplications, allCandidates, candidatesEmails)
    }
  }

  private def findDuplicates(source: List[UserApplicationProfile], population: List[UserApplicationProfile],
                             userIdsToEmails: Map[String, String]) = {
    val threeFieldsMap = population.groupBy(u => (u.firstName, u.lastName, u.dateOfBirth))
    val firstNameLastNameMap = population.groupBy(u => (u.firstName, u.lastName))
    val firstNameDoBMap = population.groupBy(u => (u.firstName, u.dateOfBirth))
    val lastNameDoBMap = population.groupBy(u => (u.lastName, u.dateOfBirth))

    source.flatMap { s =>
      // "s" (source candidate) will be part of the list because it matches with itself in all fields
      val duplicatesInThreeFields = threeFieldsMap.getOrElse((s.firstName, s.lastName, s.dateOfBirth), Nil)

      val duplicatesFirstNameLastName = firstNameLastNameMap
        .getOrElse((s.firstName, s.lastName), Nil)
        .filterNot(duplicatesInThreeFields.contains(_))
      val duplicatesFirstNameDoB = firstNameDoBMap
        .getOrElse((s.firstName, s.dateOfBirth), Nil)
        .filterNot(duplicatesInThreeFields.contains(_))
      val duplicatesDoBLastName = lastNameDoBMap
        .getOrElse((s.lastName, s.dateOfBirth), Nil)
        .filterNot(duplicatesInThreeFields.contains(_))

      // "s" (source candidate) matches with itself in more than 2 fields. Therefore, it will not be part of any
      // duplicates*InTwoFields lists. It needs to be added "manually" as the head to be present in the final report.
      val duplicatesInTwoFields = s ::
        duplicatesFirstNameLastName ++
        duplicatesFirstNameDoB ++
        duplicatesDoBLastName

      List(
        selectDuplicatesOnlyOpt(HighProbabilityMatchGroup, duplicatesInThreeFields, userIdsToEmails),
        selectDuplicatesOnlyOpt(MediumProbabilityMatchGroup, duplicatesInTwoFields, userIdsToEmails)
      ).flatten
    }
  }

  private def selectDuplicatesOnlyOpt(matchGroup: Int, duplicatesGroup: List[UserApplicationProfile],
                                      userIdsToEmails: Map[String, String]) = {
    def toDuplicateCandidate(app: UserApplicationProfile) = {
      DuplicateCandidate(
        userIdsToEmails.getOrElse(app.userId, ""),
        app.firstName,
        app.lastName,
        app.latestProgressStatus
      )
    }

    if (duplicatesGroup.size > 1) {
      val duplicateCandidates = duplicatesGroup.map(toDuplicateCandidate)
      Some(DuplicateApplicationGroup(matchGroup, duplicateCandidates))
    } else {
      None
    }
  }
}

