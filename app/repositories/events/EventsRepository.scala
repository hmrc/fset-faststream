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

package repositories.events

import config.MicroserviceAppConfig
import model.Exceptions.EventNotFoundException
import model.persisted.eventschedules._
import model.Exceptions.{ CannotUpdateSchemePreferences, EventNotFoundException }
import model.persisted.eventschedules.{ Event, EventType, Location, Venue }
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.DateTime
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.{ BSONDateTimeHandler, CollectionNames, CommonBSONDocuments, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import repositories.BSONDateTimeHandler
import repositories.BSONMapStringIntHandler

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

trait EventsRepository {
  def save(events: List[Event]): Future[Unit]
  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[Event]]
  def count(implicit ec: scala.concurrent.ExecutionContext): Future[Int]
  def remove(id: String): Future[Unit]
  def getEvent(id: String): Future[Event]
  def getEvents(eventType: Option[EventType] = None, venue: Option[Venue] = None,
    location: Option[Location] = None, skills: Seq[SkillType] = Nil, description: Option[String] = None): Future[List[Event]]
  def getEventsById(eventIds: Seq[String], eventType: Option[EventType] = None): Future[List[Event]]
  def getEventsManuallyCreatedAfter(dateTime: DateTime): Future[Seq[Event]]
  def updateStructure(): Future[Unit]
  def updateEvent(updatedEvent: Event): Future[Unit]
}

class EventsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Event, BSONObjectID](CollectionNames.ASSESSMENT_EVENTS,
    mongo, Event.eventFormat, ReactiveMongoFormats.objectIdFormats)
    with EventsRepository with ReactiveRepositoryHelpers {

  def save(events: List[Event]): Future[Unit] = {
    collection.bulkInsert(ordered = false)(events.map(implicitly[collection.ImplicitlyDocumentProducer](_)): _*)
      .map(_ => ())
  }

  def updateEvent(updatedEvent: Event): Future[Unit] = {
    val query = BSONDocument("id" -> updatedEvent.id)
    collection.update(query, updatedEvent).map(_ => ())
  }

  def getEvent(id: String): Future[Event] = {
    collection.find(BSONDocument("id" -> id), BSONDocument("_id" -> false)).one[Event] map {
      case Some(event) => event
      case None => throw EventNotFoundException(s"No event found with id $id")
    }
  }

  def remove(id: String): Future[Unit] = {
    val validator = singleRemovalValidator(id, actionDesc = "deleting event")

    collection.remove(BSONDocument("id" -> id)) map validator
  }

  def getEvents(eventType: Option[EventType] = None, venueType: Option[Venue] = None,
    location: Option[Location] = None, skills: Seq[SkillType] = Nil, description: Option[String] = None
  ): Future[List[Event]] = {
    def buildVenueTypeFilter(venueType: Option[Venue]) =
      venueType.filterNot(_.name == MicroserviceAppConfig.AllVenues.name).map { v => BSONDocument("venue.name" -> v.name) }

    def buildLocationFilter(location: Option[Location]) = location.map { locationVal => BSONDocument("location" -> locationVal) }

    def buildSkillsFilter(skills: Seq[SkillType]) = {
      if (skills.nonEmpty) {
        Some(BSONDocument("$or" -> BSONArray(
          skills.map(s => BSONDocument(s"skillRequirements.$s" -> BSONDocument("$gte" -> 1)))
        )))
      } else {
        None
      }
    }

    def buildDescriptionFilter(description: Option[String]) = description.map { descriptionVal => BSONDocument("description" -> descriptionVal) }

    val query = List(
      buildEventTypeFilter(eventType),
      buildVenueTypeFilter(venueType),
      buildLocationFilter(location),
      buildSkillsFilter(skills),
      buildDescriptionFilter(description)
    ).flatten.fold(BSONDocument.empty)(_ ++ _)

    collection.find(query).cursor[Event]().collect[List]()
  }

  def getEventsManuallyCreatedAfter(dateTime: DateTime): Future[Seq[Event]] = {
    val query = BSONDocument("createdAt" -> BSONDocument("$gte" -> dateTime.getMillis), "wasBulkUploaded" -> false)
    collection.find(query).cursor[Event]().collect[Seq]()
  }

  private def buildEventTypeFilter(eventType: Option[EventType]) =
    eventType.filterNot(_ == EventType.ALL_EVENTS).map { eventTypeVal => BSONDocument("eventType" -> eventTypeVal.toString) }

  def getEventsById(eventIds: Seq[String], eventType: Option[EventType] = None): Future[List[Event]] = {
    val query = List(
      buildEventTypeFilter(eventType),
      Option(BSONDocument("id" -> BSONDocument("$in" -> eventIds)))
    ).flatten.fold(BSONDocument.empty)(_ ++ _)

    collection.find(query).cursor[Event]().collect[List]()
  }

  def updateStructure(): Future[Unit] = {
    val updateQuery = BSONDocument("$set" -> BSONDocument("wasBulkUploaded" -> false, "createdAt" -> DateTime.now.getMillis))
    collection.update(BSONDocument.empty, updateQuery, multi = true).map(_ => ())
  }
}
