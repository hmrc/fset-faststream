/*
 * Copyright 2016 HM Revenue & Customs
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

import java.util

import model.Exceptions.{ NoSuchVenueDateException, NoSuchVenueException }
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.yaml.snakeyaml.Yaml
import play.api.Play
import play.api.libs.json.Json
import resource._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class AssessmentCentreLocation(locationName: String, venues: List[AssessmentCentreVenue])

case class AssessmentCentreVenue(venueName: String, venueDescription: String, capacityDates: List[AssessmentCentreVenueCapacityDate])

case class AssessmentCentreVenueCapacityDate(date: LocalDate, amCapacity: Int, pmCapacity: Int)

object AssessmentCentreLocation {
  implicit val assessmentCentreCapacityDateFormat = Json.format[AssessmentCentreVenueCapacityDate]
  implicit val assessmentCentreVenueFormat = Json.format[AssessmentCentreVenue]
  implicit val assessmentCentreLocationFormat = Json.format[AssessmentCentreLocation]
}

trait AssessmentCentreRepository {

  def locationsAndAssessmentCentreMapping: Future[Map[String, String]]

  def assessmentCentreCapacities: Future[List[AssessmentCentreLocation]]

  def assessmentCentreCapacityDate(venue: String, date: LocalDate): Future[AssessmentCentreVenueCapacityDate]
}

trait AssessmentCentreRepositoryImpl extends AssessmentCentreRepository {

  import play.api.Play.current

  val assessmentCentresLocationsPath: String
  val assessmentCentresConfigPath: String

  private lazy val locationsAndAssessmentCentreMappingCached = Future.successful {
    val input = managed(Play.application.resourceAsStream(assessmentCentresLocationsPath).get)
    input.acquireAndGet(file => asMapOfObjects(new Yaml().load(file)))
  }

  private lazy val assessmentCentreCapacitiesCached = Future.successful {
    val input = managed(Play.application.resourceAsStream(assessmentCentresConfigPath).get)
    input.acquireAndGet(file => asAssessmentCentreCapacity(new Yaml().load(file)))
  }

  def locationsAndAssessmentCentreMapping: Future[Map[String, String]] = {
    locationsAndAssessmentCentreMappingCached
  }

  def assessmentCentreCapacities: Future[List[AssessmentCentreLocation]] = {
    assessmentCentreCapacitiesCached
  }

  def assessmentCentreCapacityDate(venue: String, date: LocalDate): Future[AssessmentCentreVenueCapacityDate] = {
    assessmentCentreCapacities.map(
      _.flatMap(
        _.venues.find(_.venueName == venue)
      ).headOption.getOrElse(throw new NoSuchVenueException(s"The venue $venue does not exist"))
        .capacityDates.find(_.date == date)
        .getOrElse(throw new NoSuchVenueDateException(s"There are no session on that date at venue $venue"))
    )
  }

  private def asMapOfObjects[A](obj: A): Map[String, String] =
    obj.asInstanceOf[util.LinkedHashMap[String, String]].asScala.toMap

  def asAssessmentCentreCapacity[A](obj: A): List[AssessmentCentreLocation] = {
    val AssessmentCentreDateFormat = DateTimeFormat.forPattern("d/M/yy")

    // TODO: This java library forces creation of this complex statement. Investigate alternatives.
    // scalastyle:off
    val root = obj.asInstanceOf[util.LinkedHashMap[String, util.ArrayList[util.LinkedHashMap[String, util.LinkedHashMap[String, _]]]]].asScala
    // scalastyle:on

    val assessmentCentres = root.map {
      case (loc, venues) =>
        AssessmentCentreLocation(loc, venues.flatMap { venueSection =>
          venueSection.map {
            case (venueName, venueKeys) =>
              val description = venueKeys("description").asInstanceOf[String]
              val capacitiesList = venueKeys("capacities").asInstanceOf[util.ArrayList[util.LinkedHashMap[String, String]]]
              AssessmentCentreVenue(venueName, description, capacitiesList.map { capacityBlock =>
                AssessmentCentreVenueCapacityDate(
                  LocalDate.parse(capacityBlock("date"), AssessmentCentreDateFormat),
                  capacityBlock("amCapacity").asInstanceOf[Int],
                  capacityBlock("pmCapacity").asInstanceOf[Int]
                )
              }.toList)
          }.toList
        }.toList)
    }.toList
    assessmentCentres
  }
}

object AssessmentCentreYamlRepository extends AssessmentCentreRepositoryImpl {
  import config.MicroserviceAppConfig.{ assessmentCentresConfig, assessmentCentresLocationsConfig }

  override val assessmentCentresConfigPath = assessmentCentresConfig.yamlFilePath
  override val assessmentCentresLocationsPath = assessmentCentresLocationsConfig.yamlFilePath

}
