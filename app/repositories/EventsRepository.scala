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

package repositories

import config.MicroserviceAppConfig
import model.Scheme
import model.persisted.eventschedules.EventType.EventType
import model.persisted.eventschedules.{ Event, Session }
import net.jcazevedo.moultingyaml._
import net.jcazevedo.moultingyaml.DefaultYamlProtocol._
import org.joda.time.{ LocalDate, LocalTime }
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import play.api.Play
import resource._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source


object EventConfigProtocol extends DefaultYamlProtocol {
  implicit object LocalDateYamlFormat extends YamlFormat[LocalDate] {
    def write(x: LocalDate) = YamlString(x.toString("yyyy-MM-dd"))
    def read(value: YamlValue) = value match {
      case YamlDate(x) => x.toLocalDate
      case x =>
        deserializationError("Expected Date as YamlDate, but got " + x)
    }
  }

  implicit object LocalTimeYamlFormat extends YamlFormat[LocalTime] {
    def write(x: LocalTime) = YamlString(x.toString("HH:mm"))
    def read(value: YamlValue) = value match {
      case YamlString(x) => DateTimeFormat.forPattern("HH:mm").parseLocalTime(x)
      case x =>
        deserializationError("Expected Time as YamlString, but got " + x)
    }
  }

  implicit val sessionFormat = yamlFormat3((a: String, b: LocalTime, c: LocalTime) => Session(a,b,c))
  implicit val eventFormat = yamlFormat13((a: String, b: String, c: String, d: String,
                                           e: String, f: LocalDate, g: Int, h: Int, i: Int,
                                           j: LocalTime, k: LocalTime, l: String, m: List[Session]) =>
    Event.load(a,b,c,d,e,f,g,h,i,j,k,l,m))
}

trait EventRepositoryImpl {

  import play.api.Play.current

  private lazy val rawConfig = {
    val input = managed(Play.application.resourceAsStream(MicroserviceAppConfig.eventsConfig.yamlFilePath).get)
    input.acquireAndGet(stream => Source.fromInputStream(stream).mkString)
  }

  lazy val events = Future {
    import EventConfigProtocol._

    rawConfig.parseYaml.convertTo[List[Event]]
  }
}

object EventYamlRepository extends EventRepositoryImpl
