/*
 * Copyright 2021 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import javax.inject.{ Inject, Singleton }
import model.stc.StcEventTypes.{ StcEventType, StcEvents }
import model.stc.{ AuditEvent, DataStoreEvent, EmailEvent }
import play.api.Logger
import play.api.mvc.RequestHeader
import services.stc.handler._

import scala.language.implicitConversions
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class StcEventServiceImpl @Inject() (override val dataStoreEventHandler: DataStoreEventHandler,
                                     override val auditEventHandler: AuditEventHandler,
                                     override val emailEventHandler: EmailEventHandler
                                    ) extends StcEventService {

  // TODO: Error handling
  protected[stc] def handle(events: StcEvents)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val result = events.collect {
      case event: DataStoreEvent => dataStoreEventHandler.handle(event)
      case event: AuditEvent => auditEventHandler.handle(event) //TODO:fix
      case event: EmailEvent => emailEventHandler.handle(event)
      case e => Future.successful {
        Logger.warn(s"Unknown event type: $e")
      }
    }

    Future.sequence(result) map (_ => ())
  }
}

@ImplementedBy(classOf[StcEventServiceImpl])
trait StcEventService {
  protected[stc] val dataStoreEventHandler: DataStoreEventHandler
  protected[stc] val auditEventHandler: AuditEventHandler
  protected[stc] val emailEventHandler: EmailEventHandler

  protected[stc] implicit def toEvents(e: StcEventType): StcEvents = List(e)

  // TODO: Error handling
  protected[stc] def handle(events: StcEvents)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]

  protected[stc] def handle(event: StcEventType)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = handle(List(event))
}

trait EventSink {
  val eventService: StcEventService

  def eventSink(block: => Future[StcEvents])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = block.flatMap { events =>
    eventService.handle(events)
  }

  def eventSink(block: StcEvents)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventService.handle(block)
}
