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

import config.MicroserviceAppConfig
import model.Exceptions.EventNotFoundException
import model.persisted.eventschedules.{ Event, EventType, Location, Venue }
import model.persisted.eventschedules.EventType.EventType
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.CollectionNames
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EventsRepository {
  def save(events: List[Event]): Future[Unit]
  def getEvent(id: String): Future[Event]
  def getEvents(eventType: Option[EventType] = None, venue: Option[Venue] = None,
    location: Option[Location] = None, skills: Option[List[String]] = None): Future[List[Event]]
}

class EventsMongoRepository(implicit mongo: () => DB)
    extends ReactiveRepository[Event, BSONObjectID](
      CollectionNames.ASSESSMENT_EVENTS,
      mongo, Event.eventFormat, ReactiveMongoFormats.objectIdFormats
    )
    with EventsRepository {

  def save(events: List[Event]): Future[Unit] = {
    collection.bulkInsert(ordered = false)(events.map(implicitly[collection.ImplicitlyDocumentProducer](_)): _*)
      .map(_ => ())
  }

  def getEvent(id: String): Future[Event] = {
    collection.find(BSONDocument("id" -> id), BSONDocument("_id" -> false)).one[Event] map {
      case Some(event) => event
      case None => throw EventNotFoundException(s"No event found with id $id")
    }
  }

  def getEvents(eventType: Option[EventType] = None, venueType: Option[Venue] = None,
    location: Option[Location] = None, skills: Option[List[String]] = None): Future[List[Event]] = {
    val query = List(
      eventType.filterNot(_ == EventType.ALL_EVENTS).map { eventTypeVal => BSONDocument("eventType" -> eventTypeVal.toString) },
      venueType.filterNot(_ == MicroserviceAppConfig.AllVenues).map { v => BSONDocument("venue" -> v) },
      location.map { locationVal => BSONDocument("location" -> locationVal) },
      skills.map { skillsVal =>
        val skillsPartialQueries = skillsVal.map { skillVal =>
          s"skillRequirements.$skillVal" -> BSONDocument("$gte" -> 1)
        }
        BSONDocument("$or" -> BSONArray.apply(skillsPartialQueries.map(BSONDocument(_))))
      }
    ).flatten.fold(BSONDocument.empty)(_ ++ _)

    collection.find(query).cursor[Event]().collect[List]()
  }
}
