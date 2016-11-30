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

package services.personaldetails

import model.ApplicationStatus._
import model.command.PersonalDetails
import model.persisted.{ ContactDetails, PersonalDetails }
import repositories._
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.NorthSouthIndicatorCSVRepository.calculateFsacIndicator
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.AuditService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object PersonalDetailsService extends PersonalDetailsService {
  val pdRepository = faststreamPersonalDetailsRepository
  val cdRepository = faststreamContactDetailsRepository
  val csedRepository = civilServiceExperienceDetailsRepository
  val auditService = AuditService
}

trait PersonalDetailsService {
  val pdRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val csedRepository: CivilServiceExperienceDetailsRepository
  val auditService: AuditService

  def update(applicationId: String, userId: String, personalDetails: model.command.PersonalDetails): Future[Unit] = {
    val personalDetailsToPersist = model.persisted.PersonalDetails(personalDetails.firstName, personalDetails.lastName, personalDetails.preferredName,
      personalDetails.dateOfBirth, personalDetails.edipCompleted)
    val contactDetails = ContactDetails(personalDetails.outsideUk, personalDetails.address, personalDetails.postCode,
      personalDetails.country, personalDetails.email, personalDetails.phone)

    val updatePersonalDetailsFut = personalDetails.updateApplicationStatus match {
      case Some(true) => pdRepository.update(applicationId, userId, personalDetailsToPersist, List(CREATED, IN_PROGRESS), IN_PROGRESS)
      case Some(false) => pdRepository.updateWithoutStatusChange(applicationId, userId, personalDetailsToPersist)
      case None => throw new IllegalArgumentException("Update application status must be set for update operation")
    }

    val contactDetailsFut = cdRepository.update(userId, contactDetails)
    val civilServiceExperienceDetailsFut = personalDetails.civilServiceExperienceDetails.map { civilServiceExperienceDetails =>
      csedRepository.update(applicationId, civilServiceExperienceDetails)
    } getOrElse Future.successful(())

    for {
      _ <- updatePersonalDetailsFut
      _ <- contactDetailsFut
      _ <- civilServiceExperienceDetailsFut
    } yield {}
  }

  def find(applicationId: String, userId: String): Future[model.command.PersonalDetails] = {
    val personalDetailsFut = pdRepository.find(applicationId)
    val contactDetailsFut = cdRepository.find(userId)
    val civilServiceExperienceDetailsFut = csedRepository.find(applicationId)

    for {
      personalDetails <- personalDetailsFut
      contactDetails <- contactDetailsFut
      civilServiceExperienceDetails <- civilServiceExperienceDetailsFut
    } yield model.command.PersonalDetails(personalDetails.firstName, personalDetails.lastName, personalDetails.preferredName,
      contactDetails.email, personalDetails.dateOfBirth, contactDetails.outsideUk, contactDetails.address, contactDetails.postCode,
      calculateFsacIndicator(contactDetails.postCode, contactDetails.outsideUk),
      contactDetails.country, contactDetails.phone, civilServiceExperienceDetails, personalDetails.edipCompleted)
  }

}
