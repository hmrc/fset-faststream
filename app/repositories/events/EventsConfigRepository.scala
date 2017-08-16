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

import common.FutureEx
import config.MicroserviceAppConfig
import factories.UUIDFactory
import model.{ FsbType, TelephoneInterviewType }
import model.persisted.eventschedules._
import net.jcazevedo.moultingyaml._
import net.jcazevedo.moultingyaml.DefaultYamlProtocol._
import org.joda.time.{ LocalDate, LocalTime }
import org.joda.time.format.DateTimeFormat
import play.api.Play
import resource._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
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

object FsbTypeConfigProtocol extends DefaultYamlProtocol {
  implicit val format = yamlFormat3((key: String, schemeId: String, schemeCode: String) => FsbType(key, schemeId, schemeCode))
}

object TelephoneInterviewTypeConfigProtocol extends DefaultYamlProtocol {
  implicit val format = yamlFormat2((key: String, description: String) => TelephoneInterviewType(key, description))
}

object EventConfigProtocol extends DefaultYamlProtocol {
  implicit object LocalDateYamlFormat extends YamlFormat[LocalDate] {
    def write(jodaDate: LocalDate) = YamlDate(jodaDate.toDateTimeAtStartOfDay)
    def read(value: YamlValue) = value match {
      case YamlDate(jodaDateTime) => jodaDateTime.toLocalDate
      case unknown => deserializationError("Expected Date as YamlDate, but got " + unknown)
    }
  }

  implicit object LocalTimeYamlFormat extends YamlFormat[LocalTime] {
    def write(jodaTime: LocalTime) = YamlString(jodaTime.toString("HH:mm"))
    def read(value: YamlValue) = value match {
      case YamlString(stringValue) => DateTimeFormat.forPattern("HH:mm").parseLocalTime(stringValue)
      case YamlNumber(minutesSinceStartOfDay) => {
        val hour = minutesSinceStartOfDay.toInt / 60
        val minute = minutesSinceStartOfDay % 60
        DateTimeFormat.forPattern("HH:mm").parseLocalTime(s"$hour:$minute")
      }
      case x => deserializationError("Expected Time as YamlString/YamlNumber, but got " + x)
    }
  }

  implicit val sessionFormat = yamlFormat6(SessionConfig.apply)
  implicit val eventFormat = yamlFormat12(EventConfig.apply)
}

trait EventsConfigRepository {
  def locationsWithVenuesRepo: LocationsWithVenuesRepository

  import play.api.Play.current

  private def getConfig(filePath: String): String = {
    val input = managed(Play.application.resourceAsStream(filePath).get)
    input.acquireAndGet(stream => Source.fromInputStream(stream).mkString)
  }

  protected def eventScheduleConfig: String = getConfig(MicroserviceAppConfig.eventsConfig.scheduleFilePath)

  protected def fsbTypesConfig: String = getConfig(MicroserviceAppConfig.eventsConfig.subtypes.fsbFilePath)

  protected def telephoneInterviewTypesConfig: String = getConfig(MicroserviceAppConfig.eventsConfig.subtypes.telephoneInterviewFilePath)

  lazy val events: Future[List[Event]] = {
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
      } yield Event(UUIDFactory.generateUUID(),
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
          configItem.skillRequirements,
          configItem.sessions.map(s => Session(s))
      )
      eventItemFuture.recover {
        case ex => throw new Exception(
          s"Error in events config: ${MicroserviceAppConfig.eventsConfig.scheduleFilePath}. ${ex.getMessage}. ${ex.getClass.getCanonicalName}")
      }
    }
  }

  lazy val fsbTypes: Future[List[FsbType]] = Future {
    import FsbTypeConfigProtocol._
    fsbTypesConfig.parseYaml.convertTo[List[FsbType]]
  }

  lazy val telephoneInterviewTypes: Future[List[TelephoneInterviewType]] = Future {
    import TelephoneInterviewTypeConfigProtocol._
    telephoneInterviewTypesConfig.parseYaml.convertTo[List[TelephoneInterviewType]]
  }
}

object EventsConfigRepository extends EventsConfigRepository {
  val locationsWithVenuesRepo = LocationsWithVenuesInMemoryRepository
}
