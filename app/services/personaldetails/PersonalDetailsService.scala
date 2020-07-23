/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import model.ApplicationStatus._
import model.Exceptions.FSACCSVIndicatorNotFound
import model.FSACIndicator
import model.command.GeneralDetails
import model.persisted.{ ContactDetails, PersonalDetails }
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.csv.FSACIndicatorCSVRepository
import repositories.fsacindicator.FSACIndicatorRepository
import repositories.personaldetails.PersonalDetailsRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class PersonalDetailsService @Inject() (pdRepository: PersonalDetailsRepository,
                                         cdRepository: ContactDetailsRepository,
                                         csedRepository: CivilServiceExperienceDetailsRepository,
                                         fsacIndicatorCSVRepository: FSACIndicatorCSVRepository,
                                         fsacIndicatorRepository: FSACIndicatorRepository
                                         //  val auditService: AuditService //not used
                                        ) {

  def find(applicationId: String, userId: String): Future[GeneralDetails] = {
    val personalDetailsFut = pdRepository.find(applicationId)
    val contactDetailsFut = cdRepository.find(userId)
    val fsacIndicatorFut = fsacIndicatorRepository.find(applicationId)
    val civilServiceExperienceDetailsFut = csedRepository.find(applicationId)

    for {
      personalDetails <- personalDetailsFut
      contactDetails <- contactDetailsFut
      fsacIndicator <- fsacIndicatorFut
      civilServiceExperienceDetails <- civilServiceExperienceDetailsFut
    } yield GeneralDetails(personalDetails, contactDetails, FSACIndicator(fsacIndicator), civilServiceExperienceDetails)
  }

  def find(applicationId: String): Future[PersonalDetails] = {
    pdRepository.find(applicationId).map { pd => pd }
  }

  def update(applicationId: String, userId: String, personalDetails: model.command.GeneralDetails): Future[Unit] = {
    val personalDetailsToPersist = model.persisted.PersonalDetails(personalDetails.firstName, personalDetails.lastName,
      personalDetails.preferredName, personalDetails.dateOfBirth, personalDetails.edipCompleted, personalDetails.edipYear,
      personalDetails.otherInternshipCompleted, personalDetails.otherInternshipName, personalDetails.otherInternshipYear)
    val contactDetails = ContactDetails(personalDetails.outsideUk, personalDetails.address, personalDetails.postCode,
      personalDetails.country, personalDetails.email, personalDetails.phone)

    val updatePersonalDetailsFut = personalDetails.updateApplicationStatus match {
      case Some(true) => pdRepository.update(applicationId, userId, personalDetailsToPersist, List(CREATED, IN_PROGRESS), IN_PROGRESS)
      case Some(false) => pdRepository.updateWithoutStatusChange(applicationId, userId, personalDetailsToPersist)
      case None => throw new IllegalArgumentException("Update application status must be set for update operation")
    }

    val contactDetailsUpdateFut = cdRepository.update(userId, contactDetails)
    val fsacIndicator = fsacIndicatorCSVRepository.find(personalDetails.postCode, personalDetails.outsideUk)
    val fsacIndicatorUpdateFut = fsacIndicator.map { fsacIndicatorVal =>
      fsacIndicatorRepository.update(applicationId, userId,
        model.persisted.FSACIndicator(fsacIndicatorVal, fsacIndicatorCSVRepository.FSACIndicatorVersion))
    }.getOrElse(Future.failed(FSACCSVIndicatorNotFound(applicationId)))
    val civilServiceExperienceDetailsUpdateFut = personalDetails.civilServiceExperienceDetails.map { civilServiceExperienceDetails =>
      csedRepository.update(applicationId, civilServiceExperienceDetails)
    } getOrElse Future.successful(())

    for {
      _ <- updatePersonalDetailsFut
      _ <- contactDetailsUpdateFut
      _ <- fsacIndicatorUpdateFut
      _ <- civilServiceExperienceDetailsUpdateFut
    } yield {}
  }

  def updateFsacIndicator(applicationId: String, userId: String, fsacAssessmentCentre: String): Future[Unit] = {
    val validFsacAssessmentCentres = fsacIndicatorCSVRepository.getAssessmentCentres
    val msg = s"Invalid FSAC assessment centre supplied when trying to update the FSAC indicator - $fsacAssessmentCentre"
    if (!validFsacAssessmentCentres.contains(fsacAssessmentCentre)) {
      Future.failed(new IllegalArgumentException(msg))
    } else {
      fsacIndicatorRepository.update(applicationId, userId,
        model.persisted.FSACIndicator(model.FSACIndicator(fsacAssessmentCentre, fsacAssessmentCentre),
          fsacIndicatorCSVRepository.FSACIndicatorVersion
        )
      )
    }
  }
}
