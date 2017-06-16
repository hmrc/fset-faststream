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

package repositories.events

import model.persisted.eventschedules.{ Event, EventType, VenueType }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import repositories.CollectionNames
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EventsRepository {
  def save(events: List[Event]): Future[Unit]
  def fetchEvents(eventType: EventType.Value, venueType: VenueType.Value) : Future[List[Event]]
}

class EventsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Event, BSONObjectID](CollectionNames.ASSESSMENT_EVENTS,
    mongo, Event.eventFormat, ReactiveMongoFormats.objectIdFormats)
  with EventsRepository {

  override def save(events: List[Event]): Future[Unit] = {
    collection.bulkInsert(ordered = false)(events.map(implicitly[collection.ImplicitlyDocumentProducer](_)): _*)
      .map(_ => ())
  }

  override def fetchEvents(eventType: EventType.Value, venue: VenueType.Value): Future[List[Event]] = {
    val query = BSONDocument("eventType" -> eventType.toString, "venue" -> venue.toString)
    collection.find(query).cursor[Event]().collect[List]()
  }

}
