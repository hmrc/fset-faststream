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

package services.fastpass

import connectors.{ CSREmailClient, OnlineTestEmailClient }
import model.events.AuditEvents.{ FastPassUserAccepted, FastPassUserAcceptedEmailSent, FastPassUserRejected }
import model.events.DataStoreEvents.{ ApplicationReadyForExport, FastPassApproved, FastPassRejected }
import play.api.mvc.RequestHeader
import repositories._
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import services.application.ApplicationService
import services.events.{ EventService, EventSink }
import services.personaldetails.PersonalDetailsService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FastPassService extends FastPassService {
  val applicationService = ApplicationService
  val personalDetailsService = PersonalDetailsService
  val eventService: EventService = EventService
  val emailClient = CSREmailClient
  val cdRepository = faststreamContactDetailsRepository
  val csedRepository = civilServiceExperienceDetailsRepository

}

trait FastPassService extends EventSink {

  val applicationService: ApplicationService
  val personalDetailsService: PersonalDetailsService
  val eventService: EventService
  val emailClient: OnlineTestEmailClient
  val cdRepository: ContactDetailsRepository
  val csedRepository: CivilServiceExperienceDetailsRepository

  val acceptedTemplate = "fset_faststream_app_online_fast-pass_accepted"


  def processFastPassCandidate(userId: String, applicationId: String, accepted: Boolean, actionTriggeredBy: String)
                              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[(String, String)] = {
    if (accepted) { acceptFastPassCandidate(userId, applicationId, actionTriggeredBy) }
    else {rejectFastPassCandidate(userId, applicationId, actionTriggeredBy)}
  }

  private def acceptFastPassCandidate(userId: String, applicationId: String, actionTriggeredBy: String)
                                     (implicit hc: HeaderCarrier, rh: RequestHeader): Future[(String, String)] = {

    val emailFut = cdRepository.find(userId).map(_.email)
    val personalDetailsFut = personalDetailsService.find(applicationId, userId)
    val eventMap = Map("createdBy" -> actionTriggeredBy, "candidate" -> userId)
    for{
      _ <- csedRepository.evaluateFastPassCandidate(applicationId, true)
      _ <- eventSink(FastPassUserAccepted(eventMap) :: FastPassApproved(applicationId, actionTriggeredBy) :: Nil)
      _ <- applicationService.markForExportToParity(applicationId)
      _ <- eventSink(model.events.AuditEvents.ApplicationReadyForExport(eventMap) :: ApplicationReadyForExport(applicationId) :: Nil)
      personalDetail <- personalDetailsFut
      email <- emailFut
      _ <- emailClient.sendEmailWithName(email, personalDetail.preferredName, acceptedTemplate)
      _ <- eventSink(FastPassUserAcceptedEmailSent(
        Map("email" -> email, "name" -> personalDetail.preferredName, "template" -> acceptedTemplate)) :: Nil)
    } yield (personalDetail.firstName, personalDetail.lastName)
  }

  private def rejectFastPassCandidate(userId: String, applicationId: String, actionTriggeredBy: String)
                                     (implicit hc: HeaderCarrier, rh: RequestHeader): Future[(String, String)] = {

    val personalDetailsFut = personalDetailsService.find(applicationId, userId)
    val eventMap = Map("createdBy" -> actionTriggeredBy, "candidate" -> userId)
    for{
      _ <- csedRepository.evaluateFastPassCandidate(applicationId, false)
      _ <- eventSink(FastPassUserRejected(eventMap) :: FastPassRejected(applicationId, actionTriggeredBy) :: Nil)
      personalDetail <- personalDetailsFut
    } yield (personalDetail.firstName, personalDetail.lastName)
  }

}
