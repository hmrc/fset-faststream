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

package services.generaldetails

import model.ApplicationStatus._
import model.command.GeneralDetails
import model.persisted.{ ContactDetails, PersonalDetails }
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.AuditService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CandidateDetailsService extends CandidateDetailsService {
  val pdRepository = faststreamPersonalDetailsRepository
  val cdRepository = faststreamContactDetailsRepository
  val fpdRepository = civilServiceExperienceDetailsRepository
  val auditService = AuditService
}

trait CandidateDetailsService {
  val pdRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val fpdRepository: CivilServiceExperienceDetailsRepository
  val auditService: AuditService

  def update(applicationId: String, userId: String, candidateDetails: GeneralDetails): Future[Unit] = {
    val personalDetails = PersonalDetails(candidateDetails.firstName, candidateDetails.lastName, candidateDetails.preferredName,
      candidateDetails.dateOfBirth)
    val contactDetails = ContactDetails(candidateDetails.outsideUk, candidateDetails.address, candidateDetails.postCode,
      candidateDetails.country, candidateDetails.email, candidateDetails.phone)

    val updatePersonalDetailsFut = candidateDetails.updateApplicationStatus match {
      case Some(true) => pdRepository.update(applicationId, userId, personalDetails, List(CREATED, IN_PROGRESS), IN_PROGRESS)
      case Some(false) => pdRepository.updateWithoutStatusChange(applicationId, userId, personalDetails)
      case None => throw new IllegalArgumentException("Update application status must be set for update operation")
    }

    val contactDetailsFut = cdRepository.update(userId, contactDetails)
    val civilServiceExperienceDetailsFut = candidateDetails.civilServiceExperienceDetails.map { civilServiceExperienceDetails =>
      fpdRepository.update(applicationId, civilServiceExperienceDetails)
    } getOrElse Future.successful(())

    for {
      _ <- updatePersonalDetailsFut
      _ <- contactDetailsFut
      _ <- civilServiceExperienceDetailsFut
    } yield {}
  }

  def find(applicationId: String, userId: String): Future[GeneralDetails] = {
    val personalDetailsFut = pdRepository.find(applicationId)
    val contactDetailsFut = cdRepository.find(userId)
    val civilServiceExperienceDetailsFut = fpdRepository.find(applicationId)

    for {
      personalDetails <- personalDetailsFut
      contactDetails <- contactDetailsFut
      civilServiceExperienceDetails <- civilServiceExperienceDetailsFut
    } yield GeneralDetails(personalDetails.firstName, personalDetails.lastName, personalDetails.preferredName,
      contactDetails.email, personalDetails.dateOfBirth, contactDetails.outsideUk, contactDetails.address, contactDetails.postCode,
      contactDetails.country, contactDetails.phone, civilServiceExperienceDetails)
  }

}
