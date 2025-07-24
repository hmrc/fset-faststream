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

package services.stc.handler

import com.google.inject.ImplementedBy
import connectors.{CSREmailClient, EmailClient, OnlineTestEmailClient}

import javax.inject.{Inject, Named, Singleton}
import model.stc.{EmailEvent, EmailEvents}
import play.api.Logging
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class EmailEventHandlerImpl @Inject() (@Named("CSREmailClient") val emailClient: OnlineTestEmailClient)(
  implicit ec: ExecutionContext) extends EmailEventHandler {
  //  val emailClient: EmailClient = CSREmailClient //TODO:fix changed the type
}

@ImplementedBy(classOf[EmailEventHandlerImpl])
trait EmailEventHandler extends StcEventHandler[EmailEvent] with Logging {
  val emailClient: EmailClient

  //scalastyle:off cyclomatic.complexity
  def handle(event: EmailEvent)(implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Email event ${event.name}")
    event match {
      case _: EmailEvents.ApplicationWithdrawn => emailClient.sendWithdrawnConfirmation(event.to, event.name)
      case _: EmailEvents.ApplicationSubmitted => emailClient.sendApplicationSubmittedConfirmation(event.to, event.name)
      case _: EmailEvents.ApplicationPostSubmittedCheckFailed => emailClient.sendApplicationPostSubmittedCheckFailed(event.to, event.name)
      case _: EmailEvents.ApplicationPostSubmittedCheckPassed => emailClient.sendApplicationPostSubmittedCheckPassed(event.to, event.name)
      case adjEvent: EmailEvents.AdjustmentsConfirmed =>
        emailClient.sendAdjustmentsConfirmation(adjEvent.to, adjEvent.name, adjEvent.etrayAdjustments, adjEvent.videoAdjustments)
      case adjEvent: EmailEvents.AdjustmentsChanged =>
        emailClient.sendAdjustmentsUpdateConfirmation(adjEvent.to, adjEvent.name, adjEvent.etrayAdjustments, adjEvent.videoAdjustments)
      case _: EmailEvents.ApplicationConvertedToSdip =>
        emailClient.sendApplicationExtendedToSdip(event.to, event.name)
      case e: EmailEvents.CandidateAllocationConfirmed =>
        emailClient.sendCandidateInvitationConfirmedToEvent(e.to, e.name, e.eventDate, e.eventTime,
          e.eventType, e.eventVenue, e.eventGuideUrl)
      case e: EmailEvents.CandidateAllocationConfirmationRequest => emailClient.sendCandidateConfirmationRequestToEvent(e)
      case e: EmailEvents.CandidateAllocationConfirmationReminder => emailClient.sendCandidateConfirmationRequestReminderToEvent(e)
    }
  } //scalastyle:on
}
