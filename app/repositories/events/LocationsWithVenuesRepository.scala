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

import java.util
import scala.concurrent.ExecutionContext

//import com.github.ghik.silencer.silent
import config.MicroserviceAppConfig
import model.persisted.ReferenceData
import model.persisted.eventschedules.{Location, Venue}
import org.yaml.snakeyaml.Yaml
import play.api.Application
import play.api.libs.json.{Json, OFormat}
import resource._

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

case class LocationWithVenue(name: String, venues: List[Venue])

object LocationWithVenue {
  implicit val locationWithVenueFormat: OFormat[LocationWithVenue] = Json.format[LocationWithVenue]
}

case class UnknownLocationException(m: String) extends Exception(m)

case class UnknownVenueException(m: String) extends Exception(m)

trait LocationsWithVenuesRepository {
  def locationsWithVenuesList: Future[List[LocationWithVenue]]
  def locations: Future[ReferenceData[Location]]
  def location(name: String): Future[Location]
  def venues: Future[ReferenceData[Venue]]
  def venue(name: String): Future[Venue]
}

@Singleton
class LocationsWithVenuesInMemoryYamlRepository @Inject() (application: Application,
                                                           appConfig: MicroserviceAppConfig)(
  implicit ec: ExecutionContext) extends LocationsWithVenuesRepository {

  val locationsAndVenuesFilePath: String = appConfig.locationsAndVenuesConfig.yamlFilePath

  private lazy val locationsAndVenuesCached = Future {
    val input = managed(application.environment.resourceAsStream(locationsAndVenuesFilePath).get)
    input.acquireAndGet(file => asLocationWithVenues(new Yaml().load(file)))
  }

  private lazy val locationsCached = locationsAndVenuesCached.map(_.map(Location.apply))

  private lazy val venuesCached = locationsAndVenuesCached.map { lv => lv.flatMap(_.venues) }

  override def locations: Future[ReferenceData[Location]] = {
    for (locations <- locationsCached) yield {
      ReferenceData(locations, locations.head, appConfig.AllLocations)
    }
  }

  override def location(name: String): Future[Location] = {
    locations.map(_.allValues.find(_.name == name)
      .getOrElse(throw UnknownLocationException(s"$name is not a known location for this campaign")))
  }

  override def venues: Future[ReferenceData[Venue]] =
    for (venues <- venuesCached) yield {
      ReferenceData(venues, venues.head, appConfig.AllVenues)
    }

  override def venue(name: String): Future[Venue] = {
    venues.map(_.allValues.find(_.name == name).getOrElse(throw UnknownVenueException(s"$name is not a known venue for this campaign")))
  }

  override def locationsWithVenuesList: Future[List[LocationWithVenue]] = locationsAndVenuesCached

  private def asLocationWithVenues[A](obj: A): List[LocationWithVenue] = {
    import scala.collection.JavaConverters._
    // TODO: This java library forces creation of this complex statement. Investigate alternatives.
    val root = obj.asInstanceOf[util.LinkedHashMap[String, util.ArrayList[util.LinkedHashMap[String, util.LinkedHashMap[String, _]]]]].asScala

    val locations = root.map {
      case (loc, venues) =>
        LocationWithVenue(loc, venues.asScala.flatMap { venueSection =>
          venueSection.asScala.map {
            case (venueName, venueKeys) =>
              val description = venueKeys.asScala("description").asInstanceOf[String]
              Venue(venueName, description)
          }.toList
        }.toList)
    }.toList
    locations
  }
}
