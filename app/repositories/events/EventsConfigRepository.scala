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

import common.FutureEx
import config.MicroserviceAppConfig
import factories.UUIDFactory
import io.circe.yaml.parser
import io.circe.{Decoder, Error, Json, ParsingFailure}
import model.persisted.eventschedules._
import play.api.Application

import java.time.{LocalDate, LocalTime, OffsetDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Using

case class EventConfig(
                        eventType: String,
                        description: String,
                        location: String,
                        venue: String,
                        date: LocalDate,
                        capacity: Int,
                        minViableAttendees: Int,
                        attendeeSafetyMargin: Int,
                        startTime: LocalTime,
                        endTime: LocalTime,
                        skillRequirements: Map[String, Int],
                        sessions: List[SessionConfig]
                      ) {
  override def toString: String =
    "EventConfig(" +
      s"eventType=$eventType," +
      s"description=$description," +
      s"location=$location," +
      s"venue=$venue," +
      s"date=$date," +
      s"capacity=$capacity," +
      s"minViableAttendees=$minViableAttendees," +
      s"attendeeSafetyMargin=$attendeeSafetyMargin," +
      s"startTime=$startTime," +
      s"endTime=$endTime," +
      s"skillRequirements=$skillRequirements," +
      s"sessions=$sessions" +
      ")"
}

case class SessionConfig(
                          description: String,
                          capacity: Int,
                          minViableAttendees: Int,
                          attendeeSafetyMargin: Int,
                          startTime: LocalTime,
                          endTime: LocalTime
                        ) {
  override def toString: String =
    "SessionConfig(" +
      s"description=$description," +
      s"capacity=$capacity," +
      s"minViableAttendees=$minViableAttendees," +
      s"attendeeSafetyMargin=$attendeeSafetyMargin," +
      s"startTime=$startTime," +
      s"endTime=$endTime" +
      ")"
}

object EventConfigProtocol {
  implicit val decodeSession: Decoder[SessionConfig] =
    Decoder.forProduct6(
      "description", "capacity", "minViableAttendees", "attendeeSafetyMargin", "startTime", "endTime"
    )(SessionConfig.apply)

  implicit val decodeEventConfig: Decoder[EventConfig] =
    Decoder.forProduct12(
      "eventType", "description", "location", "venue",
      "date", "capacity", "minViableAttendees", "attendeeSafetyMargin",
      "startTime", "endTime", "skillRequirements", "sessions"
    )(EventConfig.apply)
}

trait EventsConfigRepository {
  def events: Future[Seq[Event]]
}

@Singleton
class EventsConfigRepositoryImpl @Inject() (application: Application,
                                            locationsWithVenuesRepo: LocationsWithVenuesRepository,
                                            appConfig: MicroserviceAppConfig,
                                            uuidFactory: UUIDFactory)(implicit ec: ExecutionContext) extends EventsConfigRepository {

  private def getConfig(filePath: String): String = {
    Using.resource(application.environment.resourceAsStream(filePath).get) { inputStream =>
      Source.fromInputStream(inputStream).mkString
    }
  }

  protected def eventScheduleConfig: String = getConfig(appConfig.eventsConfig.scheduleFilePath)

  override lazy val events: Future[Seq[Event]] = {
    import EventConfigProtocol._

    val json: Either[ParsingFailure, Json] = parser.parse(eventScheduleConfig)

    import cats.syntax.either._

    val eventsConfig = json
      .leftMap(err => err: Error)
      .flatMap(_.as[Seq[EventConfig]])
      .valueOr(throw _)

    // Force all 'types' to be upper case and replace hyphens with underscores
    val massagedEventsConfig = eventsConfig.map(configItem => configItem.copy(
      eventType = configItem.eventType.replaceAll("\\s|-", "_").toUpperCase,
      skillRequirements = configItem.skillRequirements.map {
        case (skillName, numStaffRequired) => (skillName.replaceAll("\\s|-", "_").toUpperCase, numStaffRequired)}))

    FutureEx.traverseSerial(massagedEventsConfig) { configItem =>
      val eventItemFuture = for {
        location <- locationsWithVenuesRepo.location(configItem.location)
        venue <- locationsWithVenuesRepo.venue(configItem.venue)
      } yield Event(uuidFactory.generateUUID(),
        EventType.withName(configItem.eventType),
        configItem.description,
        location,
        venue,
        configItem.date,
        configItem.capacity,
        configItem.minViableAttendees,
        configItem.attendeeSafetyMargin,
        configItem.startTime,
        configItem.endTime,
        OffsetDateTime.now,
        configItem.skillRequirements,
        configItem.sessions.map(s => Session(s)),
        wasBulkUploaded = true
      )
      eventItemFuture.recover {
        case ex => throw new Exception(
          s"Error in events config: ${appConfig.eventsConfig.scheduleFilePath}. ${ex.getMessage}. ${ex.getClass.getCanonicalName}")
      }
    }
  }
}
