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
import factories.UUIDFactory
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
                  sessions: List[Session]
                )

object EventConfig {
  implicit def configToEvent(c: EventConfig): Event = {
    Event(UUIDFactory.generateUUID(),
      EventType.withName(c.eventType),
      c.description,
      Location(c.location),
      Venue(c.venue, ""),
      c.date,
      c.capacity,
      c.minViableAttendees,
      c.attendeeSafetyMargin,
      c.startTime,
      c.endTime,
      c.skillRequirements,
      c.sessions)
  }
}

object EventConfigProtocol extends DefaultYamlProtocol {
  implicit object LocalDateYamlFormat extends YamlFormat[LocalDate] {
    def write(x: LocalDate) = YamlDate(x.toDateTimeAtStartOfDay)
    def read(value: YamlValue) = value match {
      case YamlDate(x) => x.toLocalDate
      case x => deserializationError("Expected Date as YamlDate, but got " + x)
    }
  }

  implicit object LocalTimeYamlFormat extends YamlFormat[LocalTime] {
    def write(x: LocalTime) = YamlString(x.toString("HH:mm"))
    def read(value: YamlValue) = value match {
      case YamlString(s) => DateTimeFormat.forPattern("HH:mm").parseLocalTime(s)
      case YamlNumber(s) => {
        val hour = s.toInt / 60
        val minute = s % 60
        DateTimeFormat.forPattern("HH:mm").parseLocalTime(s"$hour:$minute")
      }
      case x => deserializationError("Expected Time as YamlString/YamlNumber, but got " + x)
    }
  }

  implicit val sessionFormat = yamlFormat3((a: String, b: LocalTime, c: LocalTime) => Session(a,b,c))
  implicit val eventFormat = yamlFormat12((a: String, b: String, c: String, d: String,
                                           e: LocalDate, f: Int, g: Int, h: Int,
                                           i: LocalTime, j: LocalTime, k: Map[String, Int], l: List[Session]) =>
    EventConfig(a,b,c,d,e,f,g,h,i,j,k,l))
}

trait EventsConfigRepository {
  import play.api.Play.current

  protected def rawConfig = {
    val input = managed(Play.application.resourceAsStream(MicroserviceAppConfig.eventsConfig.yamlFilePath).get)
    input.acquireAndGet(stream => Source.fromInputStream(stream).mkString)
  }

  lazy val events = Future {
    import EventConfigProtocol._

    val yamlAst = rawConfig.parseYaml
    val eventsConfig = yamlAst.convertTo[List[EventConfig]]
    eventsConfig.map(items => items: Event)
  }
}

object EventsConfigRepository extends EventsConfigRepository