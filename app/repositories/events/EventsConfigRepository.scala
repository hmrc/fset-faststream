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

import javax.inject.{Inject, Singleton}
import model.persisted.eventschedules._
import net.jcazevedo.moultingyaml._
import play.api.Application
import resource._

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

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
                      )

case class SessionConfig(
                          description: String,
                          capacity: Int,
                          minViableAttendees: Int,
                          attendeeSafetyMargin: Int,
                          startTime: LocalTime,
                          endTime: LocalTime
                        )

object EventConfigProtocol extends DefaultYamlProtocol {
  implicit object LocalDateYamlFormat extends YamlFormat[LocalDate] {
    // We need to convert from a java time LocalDate to a joda DateTime
    def write(javaDate: LocalDate) = YamlDate(new org.joda.time.DateTime(javaDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)))
    def read(value: YamlValue) = value match {
      case YamlDate(javaDateTime) => LocalDate.of(
        javaDateTime.toLocalDate.getYear, javaDateTime.toLocalDate.getMonthOfYear, javaDateTime.toLocalDate.getDayOfMonth
      )
      case unknown => deserializationError("Expected Date as YamlDate, but got " + unknown)
    }
  }

  implicit object LocalTimeYamlFormat extends YamlFormat[LocalTime] {
    def write(javaTime: LocalTime) = YamlString(DateTimeFormatter.ofPattern("HH:mm").format(javaTime))
    def read(value: YamlValue) = value match {
      case YamlString(stringValue) => LocalTime.parse(stringValue, DateTimeFormatter.ofPattern("H:m"))
      case YamlNumber(minutesSinceStartOfDay) =>
        val hour = minutesSinceStartOfDay.toInt / 60
        val minute = minutesSinceStartOfDay % 60
        LocalTime.parse(s"$hour:$minute", DateTimeFormatter.ofPattern("H:m"))
      case x => deserializationError("Expected Time as YamlString/YamlNumber, but got " + x)
    }
  }

  implicit val sessionFormat = yamlFormat6(SessionConfig.apply)
  implicit val eventFormat = yamlFormat12(EventConfig.apply)
}

trait EventsConfigRepository {
  def events: Future[List[Event]]
}

@Singleton
class EventsConfigRepositoryImpl @Inject() (application: Application,
                                            locationsWithVenuesRepo: LocationsWithVenuesRepository,
                                            appConfig: MicroserviceAppConfig,
                                            uuidFactory: UUIDFactory)(implicit ec: ExecutionContext) extends EventsConfigRepository {

  protected def eventScheduleConfig: String = getConfig(appConfig.eventsConfig.scheduleFilePath)

  private def getConfig(filePath: String): String = {
    val input = managed(application.environment.resourceAsStream(filePath).get)
    input.acquireAndGet(stream => Source.fromInputStream(stream).mkString)
  }

  override lazy val events: Future[List[Event]] = {
    import EventConfigProtocol._

    val yamlAst = eventScheduleConfig.parseYaml
    val eventsConfig = yamlAst.convertTo[List[EventConfig]]

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

