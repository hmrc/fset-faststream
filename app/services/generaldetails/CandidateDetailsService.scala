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
import model.command.UpdateGeneralDetails
import model.persisted.{ContactDetails, PersonalDetails}
import repositories._
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.AuditService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CandidateDetailsService extends CandidateDetailsService {
  val pdRepository = faststreamPersonalDetailsRepository
  val cdRepository = faststreamContactDetailsRepository
  val auditService = AuditService
}

trait CandidateDetailsService {
  val pdRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val auditService: AuditService

  def update(applicationId: String, userId: String, candidateDetails: UpdateGeneralDetails): Future[Unit] = {
    val personalDetails = PersonalDetails(candidateDetails.firstName, candidateDetails.lastName, candidateDetails.preferredName,
      candidateDetails.dateOfBirth)
    val contactDetails = ContactDetails(candidateDetails.outsideUk, toPersistedAddress(candidateDetails.address),
      candidateDetails.postCode, candidateDetails.email, candidateDetails.phone)

    val updatePersonalDetailsFut = pdRepository.update(applicationId, userId, personalDetails, IN_PROGRESS)
    val contactDetailsFut = cdRepository.update(userId, contactDetails)

    for {
      _ <- updatePersonalDetailsFut
      _ <- contactDetailsFut
    } yield {}
  }

  def find(applicationId: String, userId: String): Future[UpdateGeneralDetails] = {
    val personalDetailsFut = pdRepository.find(applicationId)
    val contactDetailsFut = cdRepository.find(userId)

    for {
      pd <- personalDetailsFut
      cd <- contactDetailsFut
    } yield UpdateGeneralDetails(pd.firstName, pd.lastName, pd.preferredName, cd.email, pd.dateOfBirth,
      cd.outsideUk, toRequestAddress(cd.address), cd.postCode, cd.phone)
  }

  private def toPersistedAddress(address: model.command.Address): model.persisted.Address =
    model.persisted.Address(address.line1, address.line2, address.line3, address.line4)

  private def toRequestAddress(address: model.persisted.Address) =
    model.command.Address(address.line1, address.line2, address.line3, address.line4)
}
