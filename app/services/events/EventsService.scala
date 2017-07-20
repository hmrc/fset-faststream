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

import model.persisted.eventschedules.{ Event, Venue }
import model.persisted.eventschedules.EventType.EventType
import play.api.Logger
import repositories.events.{ EventsConfigRepository, EventsMongoRepository, EventsRepository }
import repositories.eventsRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object EventsService extends EventsService {
  val eventsRepo: EventsMongoRepository = eventsRepository
  val eventsConfigRepo = EventsConfigRepository
}

trait EventsService {

  def eventsRepo: EventsRepository

  def eventsConfigRepo: EventsConfigRepository

  def saveAssessmentEvents(): Future[Unit] = {
    eventsConfigRepo.events.flatMap { events =>
      Logger.debug("Events have been processed!")
      eventsRepo.save(events)
    }
  }

  def getEvent(id: String): Future[Event] = {
    eventsRepo.getEvent(id)
  }

  def getEvents(eventType: EventType, venue: Venue): Future[List[Event]] = {
    eventsRepo.getEvents(Some(eventType), Some(venue))
  }
}
