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

package repositories

import java.util
import com.google.inject.ImplementedBy
import config.MicroserviceAppConfig

import javax.inject.{Inject, Singleton}
import model.persisted.PersonalDetails
import org.yaml.snakeyaml.Yaml
import play.api.Application
import repositories.FrameworkRepository.{CandidateHighestQualification, Framework, Location, Region}
import repositories.FrameworkYamlRepository._
import resource.managed

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@deprecated("fasttrack version. Framework need to be renamed to Scheme", "July 2016")
@ImplementedBy(classOf[FrameworkYamlRepository])
trait FrameworkRepository {
  def getFrameworksByRegion: Future[List[Region]]
  def getFrameworksByRegionFilteredByQualification(criteriaMet: CandidateHighestQualification)(
    implicit ec: ExecutionContext): Future[List[Region]] =
    getFrameworksByRegion.map { regions =>
      regions.map { region =>
        Region(region.name, region.locations.map { location =>
          Location(
            location.name,
            frameworks = location.frameworks.filter(_.criteria.value <= criteriaMet.value)
          )
        }.filter(_.frameworks.nonEmpty))
      }.filter(_.locations.nonEmpty)
    }
}

@Singleton
class FrameworkYamlRepository @Inject() (implicit application: Application, appConfig: MicroserviceAppConfig, ec: ExecutionContext)
  extends FrameworkRepository {

  private lazy val frameworks: Iterable[YamlFramework] = {
    val document = loadYamlDocument(appConfig.frameworksConfig.yamlFilePath)
    parseDocument(document)
  }

  private lazy val frameworksByRegionCached: Future[List[Region]] = Future.successful({
    val uncollatedRegions = flatMapFrameworksToRegions(frameworks)
    val collatedRegions = collateRegions(uncollatedRegions)
    val sortedCollatedRegions = sortRegions(collatedRegions)
    sortedCollatedRegions
  })

  override def getFrameworksByRegion: Future[List[Region]] =
    frameworksByRegionCached

  private def loadYamlDocument(filename: String): Map[String, _] = {
    val input = managed(application.environment.resourceAsStream(filename).get)
    val document = input.acquireAndGet(new Yaml().load(_).asInstanceOf[util.LinkedHashMap[String, _]].asScala.toMap)
    document
  }

  private def parseDocument(document: Map[String, _]): Iterable[YamlFramework] = document.map {
    case (frameworkName, frameworkObj) =>
      val frameworkMap = frameworkObj.asMapOfObjects
      val locationsMap = frameworkMap.getOrElse("Locations", Nil).asMapOfStringLists
      val criteria = frameworkMap.getOrElse("Criteria", -1).toString.toInt
      val regions = locationsMap.toList.map {
        case (regionName, locations) =>
          YamlRegion(regionName, locations)
      }
      YamlFramework(frameworkName, criteria, regions)
  }

  private def flatMapFrameworksToRegions(frameworks: Iterable[YamlFramework]): Iterable[Region] =
    for {
      framework <- frameworks
      region <- framework.regions
      locations = region.locations.map(l =>
        Location(l, List(Framework(framework.name, CandidateHighestQualification(framework.criteria)))))
    } yield Region(region.name, locations)

  private def collateRegions(uncollatedRegions: Iterable[Region]): List[Region] =
    uncollatedRegions
      .groupBy(_.name)
      .map {
        case (regionName, regionsWithSameName) =>
          val groupedLocations = regionsWithSameName.flatMap(_.locations).groupBy(_.name)
          val collatedLocations = groupedLocations.toList.map {
            case (locationName, locationsWithSameName) =>
              val collatedFrameworks = locationsWithSameName.flatMap(_.frameworks).toList
              Location(locationName, collatedFrameworks)
          }
          Region(regionName, collatedLocations)
      }
      .toList

  private def sortRegions(regions: List[Region]): List[Region] =
    regions.sortBy(_.name).map { region =>
      val sortedLocations = region.locations.sortBy(_.name).map { location =>
        val sortedFrameworks = location.frameworks.sortBy(_.name)
        location.copy(frameworks = sortedFrameworks)
      }
      region.copy(locations = sortedLocations)
    }
}

object FrameworkYamlRepository {
  case class YamlFramework(name: String, criteria: Int, regions: List[YamlRegion])
  case class YamlRegion(name: String, locations: List[String])

  implicit class YamlConversion[A](val obj: A) extends AnyVal {
    def asMapOfObjects: Map[String, _] =
      obj.asInstanceOf[util.LinkedHashMap[String, _]].asScala.toMap

    def asMapOfStringLists: Map[String, List[String]] =
      obj.asInstanceOf[util.LinkedHashMap[String, util.ArrayList[String]]].asScala.mapValues(_.asScala.toList).toMap
  }
}

object FrameworkRepository {
  case class Region(name: String, locations: List[Location])
  case class Location(name: String, frameworks: List[Framework])
  case class Framework(name: String, criteria: CandidateHighestQualification)
  case class CandidateHighestQualification(value: Int) {
    require(value >= CandidateHighestQualification.MIN)
    require(value <= CandidateHighestQualification.MAX)
  }

  object CandidateHighestQualification {
    lazy val GCSE = CandidateHighestQualification(1)
    lazy val A_LEVELS_D_PLUS = CandidateHighestQualification(2)
    lazy val A_LEVELS_C_PLUS_STEM = CandidateHighestQualification(3)
    val MIN = 1
    val MAX = 3

    def from(personalDetails: PersonalDetails): CandidateHighestQualification =
    /* From fasttrack
      if (personalDetails.stemLevel) {
        CandidateHighestQualification.A_LEVELS_C_PLUS_STEM
      } else if (personalDetails.aLevel) {
        CandidateHighestQualification.A_LEVELS_D_PLUS
      } else { */
      CandidateHighestQualification.GCSE
    // }
  }
}
