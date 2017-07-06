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

import java.io.InputStream
import java.util

import com.github.ghik.silencer.silent
import config.MicroserviceAppConfig
import model.persisted.eventschedules.{ Location, Venue }
import org.yaml.snakeyaml.Yaml
import play.api.Play
import play.api.libs.json.{ Json, OFormat }
import resource._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

case class LocationWithVenue(name: String, venues: List[Venue])
object LocationWithVenue { implicit val locationWithVenueFormat: OFormat[LocationWithVenue] = Json.format[LocationWithVenue] }

case class UnknownLocationException(m: String) extends Exception(m)
case class UnknownVenueException(m: String) extends Exception(m)

trait LocationsWithVenuesRepository {
  def locationsWithVenuesList: Future[List[LocationWithVenue]]
  def locations: Future[Set[Location]]
  def location(name: String): Future[Location]
  def venues: Future[Set[Venue]]
  def venue(name: String): Future[Venue]
}

trait LocationsWithVenuesYamlRepository extends LocationsWithVenuesRepository {

  import play.api.Play.current

  val locationsAndVenuesFilePath: String

  private lazy val locationsAndVenuesCached = Future {
    @silent val input = managed(Play.application.resourceAsStream(locationsAndVenuesFilePath).get)
    input.acquireAndGet(file => asLocationWithVenues(new Yaml().load(file)))
  }

  private lazy val allLocationsCached = locationsAndVenuesCached.map { lv =>
    lv.map(Location.apply) :+ MicroserviceAppConfig.AllLocations toSet
  }

  private lazy val allVenuesCached = locationsAndVenuesCached.map { lv =>
    lv.flatMap(_.venues) :+ MicroserviceAppConfig.AllVenues toSet
  }

  def locations: Future[Set[Location]] = allLocationsCached

  def location(name: String): Future[Location] = {
    locations.map(_.find(_.name == name).getOrElse(throw UnknownLocationException(s"$name is not a known location for this campaign")))
  }

  def venues: Future[Set[Venue]] = allVenuesCached

  def venue(name: String): Future[Venue] = {
    venues.map(_.find(_.name == name).getOrElse(throw UnknownVenueException(s"$name is not a known venue for this campaign")))
  }

  def locationsWithVenuesList: Future[List[LocationWithVenue]] = locationsAndVenuesCached

  def asLocationWithVenues[A](obj: A): List[LocationWithVenue] = {
    // TODO: This java library forces creation of this complex statement. Investigate alternatives.
    val root = obj.asInstanceOf[util.LinkedHashMap[String, util.ArrayList[util.LinkedHashMap[String, util.LinkedHashMap[String, _]]]]].asScala

    val locations = root.map {
      case (loc, venues) =>
        LocationWithVenue(loc, venues.flatMap { venueSection =>
          venueSection.map {
            case (venueName, venueKeys) =>
              val description = venueKeys("description").asInstanceOf[String]
              Venue(venueName, description)
          }.toList
        }.toList)
    }.toList
    locations
  }
}

object LocationsWithVenuesInMemoryRepository extends LocationsWithVenuesYamlRepository {
  import config.MicroserviceAppConfig.locationsAndVenuesConfig
  val locationsAndVenuesFilePath: String = locationsAndVenuesConfig.yamlFilePath
}
