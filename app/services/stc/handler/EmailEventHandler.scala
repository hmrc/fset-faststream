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

package services.stc.handler

import connectors.{ CSREmailClient, EmailClient }
import model.stc.{ EmailEvent, EmailEvents }
import play.api.Logger
import play.api.mvc.RequestHeader
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

object EmailEventHandler extends EmailEventHandler {
  val emailClient: EmailClient = CSREmailClient
}

trait EmailEventHandler extends StcEventHandler[EmailEvent] {
  val emailClient: EmailClient

  def handle(event: EmailEvent)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    Logger.info(s"Email event ${event.name}")
    event match {
      case _: EmailEvents.ApplicationWithdrawn => emailClient.sendWithdrawnConfirmation(event.to, event.name)
      case _: EmailEvents.ApplicationSubmitted => emailClient.sendApplicationSubmittedConfirmation(event.to, event.name)
      case adjEvent: EmailEvents.AdjustmentsConfirmed =>
        emailClient.sendAdjustmentsConfirmation(adjEvent.to, adjEvent.name, adjEvent.etrayAdjustments, adjEvent.videoAdjustments)
      case adjEvent: EmailEvents.AdjustmentsChanged =>
        emailClient.sendAdjustmentsUpdateConfirmation(adjEvent.to, adjEvent.name, adjEvent.etrayAdjustments, adjEvent.videoAdjustments)
      case _: EmailEvents.ApplicationConvertedToSdip =>
        emailClient.sendApplicationExtendedToSdip(event.to, event.name)
      case e: EmailEvents.CandidateAllocationConfirmed =>
        emailClient.sendCandidateInvitationConfirmedToFSAC(e.to, e.name, e.eventDate, e.eventTime)
      case e: EmailEvents.CandidateAllocationConfirmationRequest =>
        emailClient.sendCandidateConfirmationRequestToFSAC(e.to, e.name, e.eventDate, e.eventTime, e.deadlineDate)
      case e: EmailEvents.AssessorNewEventCreated =>
        emailClient.sendAsssessorNotifyNewEvent(e.to, e.name, e.eventDate)


    }
  }
}
