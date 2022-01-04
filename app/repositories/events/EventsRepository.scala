/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import config.MicroserviceAppConfig

import javax.inject.{Inject, Singleton}
import model.Exceptions.EventNotFoundException
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.SkillType.SkillType
import model.persisted.eventschedules.{Event, EventType, Location, Venue}
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson.{BSONArray, BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.{CollectionNames, ReactiveRepositoryHelpers}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

trait EventsRepository {
  def save(events: List[Event]): Future[Unit]
  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[Event]]
  // Implemented by Hmrc ReactiveRepository class - don't use until it gets fixed. Use countLong instead
  @deprecated("At runtime throws a JsResultException: errmsg=readConcern.level must be either 'local', 'majority' or 'linearizable'", "")
  def count(implicit ec: scala.concurrent.ExecutionContext): Future[Int]
  // Implemented in ReactiveRespositoryHelpers
  def countLong(implicit ec: scala.concurrent.ExecutionContext): Future[Long]
  def remove(id: String): Future[Unit]
  def getEvent(id: String): Future[Event]
  def getEvents(eventType: Option[EventType] = None, venue: Option[Venue] = None,
    location: Option[Location] = None, skills: Seq[SkillType] = Nil, description: Option[String] = None): Future[List[Event]]
  def getEventsById(eventIds: Seq[String], eventType: Option[EventType] = None): Future[List[Event]]
  def getEventsManuallyCreatedAfter(dateTime: DateTime): Future[Seq[Event]]
  def updateStructure(): Future[Unit]
  def updateEvent(updatedEvent: Event): Future[Unit]
  def findAllForExtract(implicit mat: Materializer): Source[JsValue, _]
}

@Singleton
class EventsMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent, appConfig: MicroserviceAppConfig)
  extends ReactiveRepository[Event, BSONObjectID](
    CollectionNames.ASSESSMENT_EVENTS,
    mongoComponent.mongoConnector.db,
    Event.eventFormat,
    ReactiveMongoFormats.objectIdFormats)
    with EventsRepository with ReactiveRepositoryHelpers {

  private val unlimitedMaxDocs = -1

  override def indexes: Seq[Index] = Seq(
    Index(Seq(("eventType", Ascending), ("date", Ascending), ("location", Ascending), ("venue", Ascending)), unique = false)
  )

  override def save(events: List[Event]): Future[Unit] = {
    collection.insert(ordered = false).many(events)
      .map(_ => ())
  }

  override def updateEvent(updatedEvent: Event): Future[Unit] = {
    val query = BSONDocument("id" -> updatedEvent.id)
    collection.update(ordered = false).one(query, updatedEvent).map(_ => ())
  }

  override def getEvent(id: String): Future[Event] = {
    collection.find(BSONDocument("id" -> id), Some(BSONDocument("_id" -> false))).one[Event] map {
      case Some(event) => event
      case None => throw EventNotFoundException(s"No event found with id $id")
    }
  }

  override def remove(id: String): Future[Unit] = {
    val validator = singleRemovalValidator(id, actionDesc = "deleting event")

    collection.delete().one(BSONDocument("id" -> id)) map validator
  }

  override def getEvents(eventType: Option[EventType] = None, venueType: Option[Venue] = None,
    location: Option[Location] = None, skills: Seq[SkillType] = Nil, description: Option[String] = None
  ): Future[List[Event]] = {
    def buildVenueTypeFilter(venueType: Option[Venue]) =
      venueType.filterNot(_.name == appConfig.AllVenues.name).map { v => BSONDocument("venue.name" -> v.name) }

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

    collection.find(query, projection = Option.empty[JsObject])
      .cursor[Event]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[Event]]())
  }

  override def getEventsManuallyCreatedAfter(dateTime: DateTime): Future[Seq[Event]] = {
    val query = BSONDocument("createdAt" -> BSONDocument("$gte" -> dateTime.getMillis), "wasBulkUploaded" -> false)
    collection.find(query, projection = Option.empty[JsObject]).cursor[Event]().collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[Event]]())
  }

  private def buildEventTypeFilter(eventType: Option[EventType]) =
    eventType.filterNot(_ == EventType.ALL_EVENTS).map { eventTypeVal => BSONDocument("eventType" -> eventTypeVal.toString) }

  override def getEventsById(eventIds: Seq[String], eventType: Option[EventType] = None): Future[List[Event]] = {
    val query = List(
      buildEventTypeFilter(eventType),
      Option(BSONDocument("id" -> BSONDocument("$in" -> eventIds)))
    ).flatten.fold(BSONDocument.empty)(_ ++ _)

    collection.find(query, projection = Option.empty[JsObject])
      .cursor[Event]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[Event]]())
  }

  override def updateStructure(): Future[Unit] = {
    val updateQuery = BSONDocument("$set" -> BSONDocument("wasBulkUploaded" -> false, "createdAt" -> DateTime.now.getMillis))
    collection.update(ordered = false).one(BSONDocument.empty, updateQuery, multi = true).map(_ => ())
  }

  override def findAllForExtract(implicit mat: Materializer): Source[JsValue, _] = {
    import reactivemongo.akkastream.cursorProducer
    val projection = Json.obj("_id" -> false)

    collection.find(BSONDocument.empty, Some(projection))
      .cursor[JsValue](ReadPreference.primaryPreferred)
      .documentSource()
  }
}
