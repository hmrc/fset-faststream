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

package services.events

import model.events.EventTypes.{ EventType, Events }
import model.events.{ AuditEvent, DataStoreEvent, EmailEvent }
import play.api.Logger
import play.api.mvc.RequestHeader
import services.events.handler.{ AuditEventHandler, EmailEventHandler, DataStoreEventHandler }
import uk.gov.hmrc.play.http.HeaderCarrier
import scala.language.implicitConversions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EventService extends EventService {
  val dataStoreEventHandler: DataStoreEventHandler = DataStoreEventHandler
  val auditEventHandler: AuditEventHandler = AuditEventHandler
  val emailEventHandler: EmailEventHandler = EmailEventHandler
}

trait EventService {
  protected[events] val dataStoreEventHandler: DataStoreEventHandler
  protected[events] val auditEventHandler: AuditEventHandler
  protected[events] val emailEventHandler: EmailEventHandler

  protected[events] implicit def toEvents(e: EventType): Events = List(e)

  // TODO: Error handling
  protected[events] def handle(events: Events)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
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

  protected[events] def handle(event: EventType)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = handle(List(event))
}

trait EventSink {
  val eventService: EventService

  def eventSink(block: => Future[Events])(implicit hc: HeaderCarrier, rh: RequestHeader) = block.flatMap { events =>
    eventService.handle(events)
  }

  def eventSink(block: Events)(implicit hc: HeaderCarrier, rh: RequestHeader) = eventService.handle(block)
}
