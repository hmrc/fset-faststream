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

package services.events

import model.events.EmailEvents.EmailEvent
import model.events.EventTypes.{ EventType, Events }
import model.events.{ AuditEvent, MongoEvent }
import play.api.Logger
import services.events.handler.{ AuditEventHandler, EmailEventHandler, MongoEventHandler }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EventService extends EventService {
  val mongoEventHandler: MongoEventHandler = MongoEventHandler
  val auditEventHandler: AuditEventHandler = AuditEventHandler
  val emailEventHandler: EmailEventHandler = EmailEventHandler
}

trait EventService {
  val mongoEventHandler: MongoEventHandler
  val auditEventHandler: AuditEventHandler
  val emailEventHandler: EmailEventHandler

  def handle(events: Events): Future[Unit] = {
    val result = events.collect {
      case event: MongoEvent => mongoEventHandler.handle(event)
      case event: AuditEvent => auditEventHandler.handle(event)
      case event: EmailEvent => emailEventHandler.handle(event)
      case e => Future.successful {
        Logger.warn(s"Unknown event type: $e")
      }
    }

    Future.sequence(result) map (_ => ())
  }

  def handle(event: EventType): Future[Unit] = handle(List(event))

}
