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

import model.persisted.UserApplicationProfile
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
    for {
      allCandidates <- reportingRepository.candidatesForDuplicateDetectionReport
      userIdToEmail <- cdRepository.findAll.map(_.groupBy(_.userId).mapValues(_.head.email))
    } yield {
      val exportedApplications = allCandidates.filter(_.exportedToParity)
      Logger.info(s"Detect duplications from ${allCandidates.length} candidates")
      Logger.info(s"Detect duplications for ${exportedApplications.length} exported candidates")
      detectDuplicates(exportedApplications, allCandidates, userIdToEmail)
    }
  }

  private def detectDuplicates(source: List[UserApplicationProfile], population: List[UserApplicationProfile],
                               userIdToEmail: Map[String, String]) = {
    source.flatMap { originalApp =>
      val duplicatesInThreeFields = population.filter(originalApp.sameFirstNameLastNameAndDOB)
      val duplicatesInTwoFields = population.filter(app => originalApp == app || originalApp.sameExactlyTwoFirstNameLastNameAndDOB(app))

      List(
        findDuplicationOpt(HighProbabilityMatchGroup, duplicatesInThreeFields, userIdToEmail),
        findDuplicationOpt(MediumProbabilityMatchGroup, duplicatesInTwoFields, userIdToEmail)
      ).flatten
    }
  }

  private def findDuplicationOpt(matchGroup: Int, duplicatesGroup: List[UserApplicationProfile],
                                 userIdToEmail: Map[String, String]) = {
    if (duplicatesGroup.size > 1) {
      val duplicateCandidates = duplicatesGroup.map(d => toDuplicateCandidate(d, userIdToEmail))
      Some(DuplicateApplicationGroup(matchGroup, duplicateCandidates))
    } else {
      None
    }
  }

  private def toDuplicateCandidate(app: UserApplicationProfile, userIdToEmail: Map[String, String]) = {
    DuplicateCandidate(userIdToEmail.getOrElse(app.userId, ""), app.firstName, app.lastName, app.latestProgressStatus)
  }
}

