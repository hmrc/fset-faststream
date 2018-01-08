/*
 * Copyright 2018 HM Revenue & Customs
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

package services.stc

import model.stc.StcEventTypes.{ StcEventType, StcEvents }
import model.stc.{ AuditEvent, DataStoreEvent, EmailEvent }
import play.api.Logger
import play.api.mvc.RequestHeader
import services.stc.handler.{ AuditEventHandler, DataStoreEventHandler, EmailEventHandler }

import scala.language.implicitConversions
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object StcEventService extends StcEventService {
  val dataStoreEventHandler: DataStoreEventHandler = DataStoreEventHandler
  val auditEventHandler: AuditEventHandler = AuditEventHandler
  val emailEventHandler: EmailEventHandler = EmailEventHandler
}

trait StcEventService {
  protected[stc] val dataStoreEventHandler: DataStoreEventHandler
  protected[stc] val auditEventHandler: AuditEventHandler
  protected[stc] val emailEventHandler: EmailEventHandler

  protected[stc] implicit def toEvents(e: StcEventType): StcEvents = List(e)

  // TODO: Error handling
  protected[stc] def handle(events: StcEvents)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val result = events.collect {
      case event: DataStoreEvent => dataStoreEventHandler.handle(event)
      case event: AuditEvent => auditEventHandler.handle(event)
      case event: EmailEvent => emailEventHandler.handle(event)
      case e => Future.successful {
        Logger.warn(s"Unknown event type: $e")
      }
    }

    Future.sequence(result) map (_ => ())
  }

  protected[stc] def handle(event: StcEventType)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = handle(List(event))
}

trait EventSink {
  val eventService: StcEventService

  def eventSink(block: => Future[StcEvents])(implicit hc: HeaderCarrier, rh: RequestHeader) = block.flatMap { events =>
    eventService.handle(events)
  }

  def eventSink(block: StcEvents)(implicit hc: HeaderCarrier, rh: RequestHeader) = eventService.handle(block)
}
