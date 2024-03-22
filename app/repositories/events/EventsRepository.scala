/*
 * Copyright 2023 HM Revenue & Customs
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

import org.apache.pekko.stream.scaladsl.Source
import config.MicroserviceAppConfig
import model.Exceptions.EventNotFoundException
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.SkillType.SkillType
import model.persisted.eventschedules.{Event, EventType, Location, Venue}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions, Projections}
import play.api.libs.json.{JsValue, Json}
import repositories.{CollectionNames, ReactiveRepositoryHelpers}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.OffsetDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait EventsRepository {
  def save(events: List[Event]): Future[Unit]
  //TODO: mongo fix
//  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[Event]]
  def findAll: Future[Seq[Event]]
  // Implemented by Hmrc ReactiveRepository class - don't use until it gets fixed. Use countLong instead
//  @deprecated("At runtime throws a JsResultException: errmsg=readConcern.level must be either 'local', 'majority' or 'linearizable'", "")
//  def count(implicit ec: scala.concurrent.ExecutionContext): Future[Int]
  // Implemented in ReactiveRespositoryHelpers
  def countLong(implicit ec: scala.concurrent.ExecutionContext): Future[Long]
  def remove(id: String): Future[Unit]
  def getEvent(id: String): Future[Event]
  def getEvents(eventType: Option[EventType] = None, venue: Option[Venue] = None,
    location: Option[Location] = None, skills: Seq[SkillType] = Nil, description: Option[String] = None): Future[Seq[Event]]
  def getEvents(eventType: EventType): Future[Seq[Event]]
  def getEventsById(eventIds: Seq[String], eventType: Option[EventType] = None): Future[Seq[Event]]
  def getEventsManuallyCreatedAfter(dateTime: OffsetDateTime): Future[Seq[Event]]
  def updateStructure(): Future[Unit]
  def updateEvent(updatedEvent: Event): Future[Unit]
  def findAllForExtract: Source[JsValue, _]
}

@Singleton
class EventsMongoRepository @Inject() (mongoComponent: MongoComponent, appConfig: MicroserviceAppConfig)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Event](
    collectionName = CollectionNames.ASSESSMENT_EVENTS,
    mongoComponent = mongoComponent,
    domainFormat = Event.eventFormat,
    indexes = Seq(
      IndexModel(ascending("eventType", "date", "location", "venue"), IndexOptions().unique(false))
    )
  ) with EventsRepository with ReactiveRepositoryHelpers {

  override def save(events: List[Event]): Future[Unit] = {
    collection.insertMany(events).toFuture().map ( _ => () )
  }

  override def findAll: Future[Seq[Event]] = {
    val query = Document.empty
    collection.find(query).toFuture()
  }

  override def updateEvent(updatedEvent: Event): Future[Unit] = {
    val query = Document("id" -> updatedEvent.id)
    val update = Document("$set" -> Codecs.toBson(updatedEvent))
    collection.updateOne(query, update).toFuture().map ( _ => () )
  }

  override def getEvent(id: String): Future[Event] = {
    collection.find(Document("id" -> id)).headOption() map {
      case Some(event) => event
      case None => throw EventNotFoundException(s"No event found with event id $id")
    }
  }

  override def remove(id: String): Future[Unit] = {
    val validator = singleRemovalValidator(id, actionDesc = "deleting event")
    collection.deleteOne(Document("id" -> id)).toFuture() map validator
  }

  override def getEvents(eventType: Option[EventType] = None, venueType: Option[Venue] = None,
                         location: Option[Location] = None, skills: Seq[SkillType] = Nil, description: Option[String] = None
                        ): Future[Seq[Event]] = {
    def buildVenueTypeFilter(venueType: Option[Venue]) =
      venueType.filterNot(_.name == appConfig.AllVenues.name).map { v => Document("venue.name" -> v.name) }

    def buildLocationFilter(location: Option[Location]) = location.map { locationVal => Document("location" -> Codecs.toBson(locationVal)) }

    def buildSkillsFilter(skills: Seq[SkillType]) = {
      if (skills.nonEmpty) {
        Some(Document("$or" ->
          skills.map(s => Document(s"skillRequirements.$s" -> Document("$gte" -> 1)))
        ))
      } else {
        None
      }
    }

    def buildDescriptionFilter(description: Option[String]) =
      description.map { descriptionVal => Document("description" -> descriptionVal) }

    val query = List(
      buildEventTypeFilter(eventType),
      buildVenueTypeFilter(venueType),
      buildLocationFilter(location),
      buildSkillsFilter(skills),
      buildDescriptionFilter(description)
    ).flatten.fold(Document.empty)(_ ++ _)

    collection.find(query).toFuture()
  }

  override def getEvents(eventType: EventType): Future[Seq[Event]] = {
    val query = List(
      buildEventTypeFilter(Some(eventType))
    ).flatten.fold(Document.empty)(_ ++ _)

    collection.find(query).toFuture()
  }

  override def getEventsManuallyCreatedAfter(dateTime: OffsetDateTime): Future[Seq[Event]] = {
    import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat // Needed to handle storing ISODate format in Mongo
    val query = Document("createdAt" -> Document("$gte" -> Codecs.toBson(dateTime)), "wasBulkUploaded" -> false)
    collection.find(query).toFuture()
  }

  private def buildEventTypeFilter(eventType: Option[EventType]) =
    eventType.filterNot(_ == EventType.ALL_EVENTS).map { eventTypeVal => Document("eventType" -> eventTypeVal.toString) }

  override def getEventsById(eventIds: Seq[String], eventType: Option[EventType] = None): Future[Seq[Event]] = {
    val query = List(
      buildEventTypeFilter(eventType),
      Option(Document("id" -> Document("$in" -> eventIds)))
    ).flatten.fold(Document.empty)(_ ++ _)

    collection.find(query).toFuture()
  }

  override def updateStructure(): Future[Unit] = {
    import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat // Needed to handle storing ISODate format in Mongo
    val updateQuery = Document("$set" -> Document("wasBulkUploaded" -> false, "createdAt" -> Codecs.toBson(OffsetDateTime.now)))
    collection.updateMany(Document.empty, updateQuery).toFuture().map(_ => ())
  }

  override def findAllForExtract: Source[JsValue, _] = {
    val projection = Projections.excludeId()
    Source.fromPublisher {
      collection.find[Document]().projection(projection).transform((doc: Document) => Json.parse(doc.toJson()),
        (e: Throwable) => throw e
      )
    }
  }
}
