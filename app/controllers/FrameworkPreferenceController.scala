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

import controllers.FrameworkPreferenceController.SecondPreferenceIntention
import model.Exceptions.PersonalDetailsNotFound
import model.{ Alternatives, LocationPreference, Preferences }
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.FrameworkRepository.CandidateHighestQualification
import repositories.application.PersonalDetailsRepository
import repositories.{ FrameworkPreferenceRepository, FrameworkRepository }
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FrameworkPreferenceController extends BaseController {
  val frameworkRepository: FrameworkRepository
  val frameworkPreferenceRepository: FrameworkPreferenceRepository
  val personalDetailsRepository: PersonalDetailsRepository
  val auditService: AuditService

  def submitFirstPreference(applicationId: String) =
    submitPreference(
      applicationId,
      tryMergePreferences = (location, loadedPreferences) =>
      Some(loadedPreferences.fold(Preferences(location, None))(_.copy(firstLocation = location))),
      "FirstLocationSaved"
    )

  def submitSecondPreference(applicationId: String) =
    submitPreference(
      applicationId,
      tryMergePreferences = (location, loadedPreferences) =>
      loadedPreferences.map(_.copy(secondLocation = Some(location), secondLocationIntended = Some(true))),
      "SecondLocationSaved"
    )

  def submitSecondPreferenceIntention(applicationId: String) = Action.async(parse.json) { implicit request =>
    implicit val jsonFormat = Json.format[SecondPreferenceIntention]

    withJsonBody[SecondPreferenceIntention] { intention =>
      frameworkPreferenceRepository.tryGetPreferences(applicationId).flatMap { oldPreferences =>
        oldPreferences
          .map(_.copy(secondLocationIntended = Some(intention.secondPreferenceIntended)))
          .map { p =>
            if (intention.secondPreferenceIntended) p else p.copy(secondLocation = None)
          }
          .filter(_.isValid)
          .fold(Future.successful(BadRequest))(
            frameworkPreferenceRepository.savePreferences(applicationId, _).map { _ =>
              auditService.logEvent(if (intention.secondPreferenceIntended) "SecondLocationIntended" else "SecondLocationNotIntended")
              Ok
            }
          )
      }
    }
  }

  def submitAlternatives(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[Alternatives] { alternatives =>
      frameworkPreferenceRepository.tryGetPreferences(applicationId).flatMap { oldPreferences =>
        oldPreferences
          .map(_.copy(alternatives = Some(alternatives)))
          .filter(_.isValid)
          .fold(Future.successful(BadRequest))(
            frameworkPreferenceRepository.savePreferences(applicationId, _).map { _ =>
              auditService.logEvent("AlternativesSubmitted")
              Ok
            }
          )
      }
    }
  }

  def getPreferences(applicationId: String) = Action.async { implicit request =>
    frameworkPreferenceRepository
      .tryGetPreferences(applicationId)
      .map { preferencesIfExist =>
        preferencesIfExist
          .map(preferences => Ok(Json.toJson(preferences)))
          .getOrElse(NotFound)
      }
  }

  private def submitPreference(
    applicationId: String,
    tryMergePreferences: (LocationPreference, Option[Preferences]) => Option[Preferences],
    auditEventName: String
  ) =
    Action.async(parse.json) { implicit request =>
      withJsonBody[LocationPreference] { preference =>
        if (preference.isValid) {
          isPreferenceAvailableToCandidate(applicationId, preference).flatMap { preferenceAvailableToCandidate =>
            if (preferenceAvailableToCandidate) {
              frameworkPreferenceRepository.tryGetPreferences(applicationId).flatMap { oldPreferences =>
                tryMergePreferences(preference, oldPreferences)
                  .filter(_.isValid)
                  .fold(Future.successful(BadRequest))(
                    frameworkPreferenceRepository.savePreferences(applicationId, _).map { _ =>
                      auditService.logEvent(auditEventName)
                      Ok
                    }
                  )
              }
            } else { Future.successful(BadRequest) }
          }
        } else { Future.successful(BadRequest) }
      }
    }

  private def isPreferenceAvailableToCandidate(applicationId: String, preference: LocationPreference): Future[Boolean] = {
    case class DenormalizedFrameworkSelection(region: String, location: String, framework: String)

    personalDetailsRepository.find(applicationId).flatMap { personalDetails =>
      val highestQualification = CandidateHighestQualification.from(personalDetails)

      frameworkRepository.getFrameworksByRegionFilteredByQualification(highestQualification).map { availableFrameworks =>

        val frameworks = preference.firstFramework :: preference.secondFramework.toList
        frameworks.forall { framework =>
          availableFrameworks
            .find(_.name == preference.region).toList
            .flatMap(_.locations)
            .find(_.name == preference.location).toList
            .flatMap(_.frameworks)
            .exists(_.name == framework)
        }
      }
    }.recover {
      case e: PersonalDetailsNotFound => false
    }
  }
}

object FrameworkPreferenceController extends FrameworkPreferenceController {
  val frameworkRepository: FrameworkRepository = repositories.frameworkRepository
  val frameworkPreferenceRepository: FrameworkPreferenceRepository = repositories.frameworkPreferenceRepository
  val personalDetailsRepository: PersonalDetailsRepository = repositories.personalDetailsRepository
  val auditService = AuditService

  private case class SecondPreferenceIntention(secondPreferenceIntended: Boolean)
}
