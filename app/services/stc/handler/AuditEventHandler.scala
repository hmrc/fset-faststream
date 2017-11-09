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

import model.stc.{ AuditEvent, AuditEventNoRequest, AuditEventWithAppId }
import play.api.Logger
import play.api.mvc.RequestHeader
import services.AuditService

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object AuditEventHandler extends AuditEventHandler {
  val auditService: AuditService = AuditService
}

trait AuditEventHandler extends StcEventHandler[AuditEvent] {
  val auditService: AuditService

  def handle(event: AuditEvent)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val sanitisedDetails = event.details - "email"
    Logger.info(s"Audit event ${event.eventName}, details: $sanitisedDetails")
    Future.successful {
      event match {
        case e: AuditEventWithAppId => auditService.logEvent(e.eventName, e.details)
        case e: AuditEventNoRequest => auditService.logEventNoRequest(e.eventName, e.details)
        case e: AuditEvent => auditService.logEvent(e.eventName)
      }
    }
  }
}
