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

package services.reporting

import javax.inject.{ Inject, Singleton }
import model.persisted.{ UserApplicationProfile, UserIdWithEmail }
import play.api.Logging
import repositories.application.ReportingRepository
import repositories.contactdetails.ContactDetailsRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DuplicateCandidate(email: String, firstName: String, lastName: String, latestProgressStatus: String, applicationRoute: String)

case class DuplicateApplicationGroup(matchType: Int, candidates: Set[DuplicateCandidate])

@Singleton
class DuplicateDetectionService @Inject() (reportingRepository: ReportingRepository,
                                           cdRepository: ContactDetailsRepository) extends Logging {
  private val HighProbabilityMatchGroup = 1
  private val MediumProbabilityMatchGroup = 2

  def findAll: Future[Seq[DuplicateApplicationGroup]] = {
    def toUserIdToEmailMap(cds: Seq[UserIdWithEmail]) = {
      cds.map(cd => cd.userId -> cd.email).toMap
    }

    for {
      allCandidates <- reportingRepository.candidatesForDuplicateDetectionReport
      userIdToEmailReference <- cdRepository.findEmails.map(toUserIdToEmailMap)
      source = allCandidates
      population = allCandidates
    } yield {
      logger.debug(s"Detect duplications from ${source.length} candidates")
      logger.debug(s"Detect duplications in ${population.length} candidates")
      findDuplicates(source, population, userIdToEmailReference).groupBy(_.candidates).mapValues(_.head).values.toList
    }
  }

  // scalastyle:off method.length
  private def findDuplicates(source: Seq[UserApplicationProfile], population: Seq[UserApplicationProfile],
                             userIdToEmailReference: Map[String, String]): Seq[DuplicateApplicationGroup] = {

    def emailWithRemovedPostPlusSignIfPresent(userId: String): String = {
      val email = userIdToEmailReference.getOrElse(userId, throw new Exception(s"Contact details not found for userId $userId"))
      // Use email with everything before the first plus for duplicate finding
      if (email.contains("+")) {
        val atAndAfter = email.substring(email.indexOf("@"))
        email.substring(0, email.indexOf("+")) + atAndAfter
      } else {
        email
      }
    }

    def normalise(str: String) = str.trim.toLowerCase()
    def takeFirstNameLastNameAndDOB(user: UserApplicationProfile) = (normalise(user.firstName), normalise(user.lastName), user.dateOfBirth)
    def takeFirstNameAndLastName(user: UserApplicationProfile) = (normalise(user.firstName), normalise(user.lastName))
    def takeFirstNameAndDOB(user: UserApplicationProfile) = (normalise(user.firstName), user.dateOfBirth)
    def takeLastNameAndDOB(user: UserApplicationProfile) = (normalise(user.lastName), user.dateOfBirth)
    def takeTransformedEmailFirstNameAndLastName(user: UserApplicationProfile) = (normalise(emailWithRemovedPostPlusSignIfPresent(user.userId)),
      normalise(user.firstName),
      normalise(user.lastName))
    def takeTransformedEmailFirstNameAndDOB(user: UserApplicationProfile) = (normalise(emailWithRemovedPostPlusSignIfPresent(user.userId)),
      normalise(user.firstName),
      user.dateOfBirth)
    def takeTransformedEmailLastNameAndDOB(user: UserApplicationProfile) = (normalise(emailWithRemovedPostPlusSignIfPresent(user.userId)),
      normalise(user.lastName),
      user.dateOfBirth)

    val firstNameLastNameDOBMap = population.groupBy(takeFirstNameLastNameAndDOB)
    val firstNameLastNameMap = population.groupBy(takeFirstNameAndLastName)
    val firstNameDOBMap = population.groupBy(takeFirstNameAndDOB)
    val lastNameDOBMap = population.groupBy(takeLastNameAndDOB)

    val transformedEmailsFirstnameLastName = population.groupBy(takeTransformedEmailFirstNameAndLastName)
    val transformedEmailsFirstNameDOB = population.groupBy(takeTransformedEmailFirstNameAndDOB)
    val transformedEmailsLastNameDOB = population.groupBy(takeTransformedEmailLastNameAndDOB)

    source.flatMap { sourceCandidate =>
      val duplicatesInFirstNameLastNameDOB = firstNameLastNameDOBMap.getOrElse(takeFirstNameLastNameAndDOB(sourceCandidate), Nil)
        .filterNot(_.userId == sourceCandidate.userId)

      val duplicatesInEmailsFirstNameLastName = transformedEmailsFirstnameLastName
        .getOrElse(takeTransformedEmailFirstNameAndLastName(sourceCandidate), Nil)
        .filterNot(duplicatesInFirstNameLastNameDOB.contains)
        .filterNot(_.userId == sourceCandidate.userId)

      val duplicatesInEmailsFirstNameDOB = transformedEmailsFirstNameDOB
        .getOrElse(takeTransformedEmailFirstNameAndDOB(sourceCandidate), Nil)
        .filterNot(duplicatesInFirstNameLastNameDOB.contains)
        .filterNot(duplicatesInEmailsFirstNameLastName.contains)
        .filterNot(_.userId == sourceCandidate.userId)

      val duplicatesInEmailsLastNameDOB = transformedEmailsLastNameDOB
        .getOrElse(takeTransformedEmailLastNameAndDOB(sourceCandidate), Nil)
        .filterNot(duplicatesInFirstNameLastNameDOB.contains)
        .filterNot(duplicatesInEmailsFirstNameLastName.contains)
        .filterNot(duplicatesInEmailsFirstNameDOB.contains)
        .filterNot(_.userId == sourceCandidate.userId)

      val threeFieldMatches = duplicatesInFirstNameLastNameDOB ++ duplicatesInEmailsFirstNameLastName ++ duplicatesInEmailsFirstNameDOB ++
        duplicatesInEmailsLastNameDOB

      val duplicatesFirstNameLastName = firstNameLastNameMap
        .getOrElse(takeFirstNameAndLastName(sourceCandidate), Nil)
        .filterNot(threeFieldMatches.contains)
        .filterNot(_.userId == sourceCandidate.userId)

      val duplicatesFirstNameDOB = firstNameDOBMap
        .getOrElse(takeFirstNameAndDOB(sourceCandidate), Nil)
        .filterNot(threeFieldMatches.contains)
        .filterNot(_.userId == sourceCandidate.userId)

      val duplicatesDOBLastName = lastNameDOBMap
        .getOrElse(takeLastNameAndDOB(sourceCandidate), Nil)
        .filterNot(threeFieldMatches.contains)
        .filterNot(_.userId == sourceCandidate.userId)

      val highProbabilityDuplicates = sourceCandidate ::
        threeFieldMatches.toList

      // source candidate matches with itself in more than 2 fields. Therefore, it will not be part of any
      // duplicates*InTwoFields lists. It needs to be added "manually" as the head to be present in the final report.
      val mediumProbabilityDuplicates = sourceCandidate ::
        duplicatesFirstNameLastName.toList ++
          duplicatesFirstNameDOB.toList ++
          duplicatesDOBLastName.toList

      List(
        selectDuplicatesOnlyOpt(HighProbabilityMatchGroup, highProbabilityDuplicates.toSet, userIdToEmailReference),
        selectDuplicatesOnlyOpt(MediumProbabilityMatchGroup, mediumProbabilityDuplicates.toSet, userIdToEmailReference)
      ).flatten
    }
  }
  // scalastyle:on method.length

  private def selectDuplicatesOnlyOpt(matchGroup: Int, duplicatesGroup: Set[UserApplicationProfile],
                                      userIdsToEmails: Map[String, String]): Option[DuplicateApplicationGroup] = {
    def toDuplicateCandidate(app: UserApplicationProfile) = {
      DuplicateCandidate(
        userIdsToEmails.getOrElse(app.userId, ""),
        app.firstName,
        app.lastName,
        app.latestProgressStatus,
        app.applicationRoute.toString
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
