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

package services.allocation

import connectors.CSREmailClient
import model.Commands.ApplicationAssessment
import model.PersistedObjects.{ AllocatedCandidate, ContactDetails }
import org.joda.time.DateTime
import play.api.Logger
import repositories._
import repositories.application.CandidateAllocationRepository
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object CandidateAllocationService extends CandidateAllocationService {
  val caRepository = candidateAllocationMongoRepository
  val aaRepository = applicationAssessmentRepository
  val cdRepository = contactDetailsRepository
  val emailClient = CSREmailClient
  val auditService = AuditService
}

trait CandidateAllocationService {
  val caRepository: CandidateAllocationRepository
  val aaRepository: ApplicationAssessmentRepository
  val cdRepository: ContactDetailsRepository
  val emailClient: CSREmailClient
  val auditService: AuditService

  implicit def headerCarrier = new HeaderCarrier()
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val ReminderEmailDaysBeforeExpiration = 3

  def nextUnconfirmedCandidateForSendingReminder: Future[Option[AllocatedCandidate]] =
    caRepository.nextUnconfirmedCandidateToSendReminder(ReminderEmailDaysBeforeExpiration)

  def sendEmailConfirmationReminder(candidate: AllocatedCandidate): Future[Unit] = {
    for {
      assessment <- aaRepository.find(candidate.applicationId)
      contactDetails <- cdRepository.find(candidate.candidateDetails.userId)
      _ <- sendEmail(candidate, contactDetails, assessment)
      _ <- caRepository.saveAllocationReminderSentDate(candidate.applicationId, DateTime.now())
    } yield {
    }
  }

  private def sendEmail(candidate: AllocatedCandidate, contactDetails: ContactDetails,
    assessment: ApplicationAssessment): Future[Unit] = {
    emailClient.sendReminderToConfirmAttendance(contactDetails.email, candidate.candidateDetails.preferredName,
      assessment.assessmentDateTime, candidate.expireDate) map { _ =>
      audit("AllocationReminderEmailSent", candidate.candidateDetails.userId, contactDetails.email)
    }
  }

  private def audit(event: String, userId: String, email: String): Unit = {
    Logger.info(s"$event for user $userId")
    auditService.logEventNoRequest(event, Map("userId" -> userId, "email" -> email))
  }
}
