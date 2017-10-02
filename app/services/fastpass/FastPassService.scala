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

package services.fastpass

import connectors.{ CSREmailClient, OnlineTestEmailClient }
import model.{ CivilServiceExperienceDetails, ProgressStatuses }
import model.stc.AuditEvents.{ FastPassUserAccepted, FastPassUserAcceptedEmailSent, FastPassUserRejected }
import model.stc.DataStoreEvents.{ ApplicationReadyForExport, FastPassApproved, FastPassRejected }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import services.stc.{ EventSink, StcEventService }
import services.personaldetails.PersonalDetailsService
import services.scheme.SchemePreferencesService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FastPassService extends FastPassService {
  override val appRepo = applicationRepository
  override val personalDetailsService = PersonalDetailsService
  override val eventService: StcEventService = StcEventService
  override val emailClient = CSREmailClient
  override val cdRepository = faststreamContactDetailsRepository
  override val csedRepository = civilServiceExperienceDetailsRepository
  override val schemePreferencesService = SchemePreferencesService
  override val schemesRepository = SchemeYamlRepository

  override val fastPassDetails = CivilServiceExperienceDetails(
    applicable = true,
    fastPassReceived = Some(true),
    fastPassAccepted = Some(true),
    certificateNumber = Some("0000000")
  )

}

trait FastPassService extends EventSink {

  val appRepo: GeneralApplicationRepository
  val personalDetailsService: PersonalDetailsService
  val eventService: StcEventService
  val emailClient: OnlineTestEmailClient
  val cdRepository: ContactDetailsRepository
  val csedRepository: CivilServiceExperienceDetailsRepository
  val schemePreferencesService: SchemePreferencesService
  val schemesRepository: SchemeRepository

  val fastPassDetails: CivilServiceExperienceDetails

  val acceptedTemplate = "fset_faststream_app_online_fast-pass_accepted"


  def processFastPassCandidate(userId: String, applicationId: String, accepted: Boolean, actionTriggeredBy: String)
                              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[(String, String)] = {
    if (accepted) { acceptFastPassCandidate(userId, applicationId, actionTriggeredBy) }
    else {rejectFastPassCandidate(userId, applicationId, actionTriggeredBy)}
  }

  def promoteToFastPassCandidate(applicationId: String, actionTriggeredBy: String)
                                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    val eventMap = Map("createdBy" -> actionTriggeredBy, "applicationId" -> applicationId)
    for {
      _ <- csedRepository.update(applicationId, fastPassDetails)
      _ <- appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.FAST_PASS_ACCEPTED)
      _ <- eventSink(model.stc.AuditEvents.ApplicationReadyForExport(eventMap) :: ApplicationReadyForExport(applicationId) :: Nil)
      _ <- eventSink(FastPassUserAccepted(eventMap) :: FastPassApproved(applicationId, actionTriggeredBy) :: Nil)

    } yield ()
  }

  private def autoProgressToSiftOrFSAC(applicationId: String): Future[Unit] = {
    val res = for {
      preferences <- schemePreferencesService.find(applicationId)
    } yield {
      val hasSiftableScheme = schemesRepository.siftableSchemeIds.intersect(preferences.schemes).nonEmpty
      if(hasSiftableScheme) {
        appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_ENTERED)
      } else {
        appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      }
    }
    res.flatMap(identity)
  }

  private def acceptFastPassCandidate(userId: String, applicationId: String, actionTriggeredBy: String)
                                     (implicit hc: HeaderCarrier, rh: RequestHeader): Future[(String, String)] = {

    val emailFut = cdRepository.find(userId).map(_.email)
    val personalDetailsFut = personalDetailsService.find(applicationId, userId)
    val eventMap = Map("createdBy" -> actionTriggeredBy, "candidate" -> userId)
    for {
      email <- emailFut
      personalDetail <- personalDetailsFut
      _ <- appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.FAST_PASS_ACCEPTED)
      _ <- autoProgressToSiftOrFSAC(applicationId)
      _ <- eventSink(model.stc.AuditEvents.ApplicationReadyForExport(eventMap) :: ApplicationReadyForExport(applicationId) :: Nil)
      _ <- csedRepository.evaluateFastPassCandidate(applicationId, accepted = true)
      _ <- eventSink(FastPassUserAccepted(eventMap) :: FastPassApproved(applicationId, actionTriggeredBy) :: Nil)
      _ <- emailClient.sendEmailWithName(email, personalDetail.preferredName, acceptedTemplate)
      _ <- eventSink(FastPassUserAcceptedEmailSent(
        Map("email" -> email, "name" -> personalDetail.preferredName, "template" -> acceptedTemplate)) :: Nil)
    } yield (personalDetail.firstName, personalDetail.lastName)
  }

  private def rejectFastPassCandidate(userId: String, applicationId: String, actionTriggeredBy: String)
                                     (implicit hc: HeaderCarrier, rh: RequestHeader): Future[(String, String)] = {

    val eventMap = Map("createdBy" -> actionTriggeredBy, "candidate" -> userId)
    for {
      personalDetail <- personalDetailsService.find(applicationId, userId)
      _ <- csedRepository.evaluateFastPassCandidate(applicationId, accepted = false)
      _ <- eventSink(FastPassUserRejected(eventMap) :: FastPassRejected(applicationId, actionTriggeredBy) :: Nil)
    } yield (personalDetail.firstName, personalDetail.lastName)
  }

}
