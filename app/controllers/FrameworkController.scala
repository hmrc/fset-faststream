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

package controllers

import controllers.FrameworkController.{ Location, Region }
import model.Exceptions.PersonalDetailsNotFound
import play.api.libs.json.{ Format, Json }
import play.api.mvc.Action
import repositories.FrameworkRepository
import repositories.FrameworkRepository.CandidateHighestQualification
import repositories.application.PersonalDetailsRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

// NOTE: This object is being used both as an implementation, and also as a traditional companion object to house
// types namespaced to the controller. It is currently performing 2 very jobs...
object FrameworkController extends FrameworkController {

  // Implementation concerns:
  override val frameworkRepository: FrameworkRepository = repositories.frameworkRepository
  override val personalDetailsRepository: PersonalDetailsRepository = repositories.personalDetailsRepository

  // API concerns:
  case class Region(name: String, locations: List[Location])
  case class Location(name: String, frameworks: List[String])

  object Location {
    implicit val jsonFormat: Format[Location] = Json.format[Location]
  }

  object Region {
    implicit val jsonFormat: Format[Region] = Json.format[Region]
  }
}

trait FrameworkController extends BaseController {

  val frameworkRepository: FrameworkRepository
  val personalDetailsRepository: PersonalDetailsRepository

  def getAvailableFrameworksWithLocations(applicationId: String) = Action.async { implicit request =>
    (for {
      personalDetails <- personalDetailsRepository.find(applicationId)
      highestQualification = CandidateHighestQualification.from(personalDetails)
      regions <- frameworkRepository.getFrameworksByRegionFilteredByQualification(highestQualification)
      regionsForJson = regions.map { region =>
        Region(region.name, region.locations.map { location =>
          Location(
            location.name,
            frameworks = location.frameworks.map(_.name)
          )
        })
      }
    } yield {
      Ok(Json.toJson(regionsForJson))
    }).recover {
      case e: PersonalDetailsNotFound => NotFound(s"Cannot find personal details for application: ${e.applicationId}")
    }
  }
}
