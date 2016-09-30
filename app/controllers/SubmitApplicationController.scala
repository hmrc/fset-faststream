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

import model.ApplicationValidator
import model.events.EventTypes.Events
import model.events.{ AuditEvents, EmailEvents, DataStoreEvents }
import play.api.mvc.Action
import repositories.FrameworkRepository.CandidateHighestQualification
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import repositories.assistancedetails.AssistanceDetailsRepository
import services.events.EventService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SubmitApplicationController extends SubmitApplicationController {
  override val pdRepository: PersonalDetailsRepository = personalDetailsRepository
  override val adRepository: AssistanceDetailsRepository = faststreamAssistanceDetailsRepository
  override val cdRepository = contactDetailsRepository
  override val frameworkPrefRepository: FrameworkPreferenceMongoRepository = frameworkPreferenceRepository
  override val frameworkRegionsRepository: FrameworkRepository = frameworkRepository
  override val appRepository: GeneralApplicationRepository = applicationRepository
  val eventService: EventService = EventService
}

trait SubmitApplicationController extends BaseController {

  val pdRepository: PersonalDetailsRepository
  val adRepository: AssistanceDetailsRepository
  val cdRepository: ContactDetailsRepository
  val frameworkPrefRepository: FrameworkPreferenceRepository
  val frameworkRegionsRepository: FrameworkRepository
  val appRepository: GeneralApplicationRepository
  val eventService: EventService

  def submitApplication(userId: String, applicationId: String) = Action.async { implicit request =>
    val generalDetailsFuture = pdRepository.find(applicationId)
    val assistanceDetailsFuture = adRepository.find(applicationId)
    val contactDetailsFuture = cdRepository.find(userId)
    val schemesLocationsFuture = frameworkPrefRepository.tryGetPreferences(applicationId)

    val result = for {
      gd <- generalDetailsFuture
      ad <- assistanceDetailsFuture
      cd <- contactDetailsFuture
      sl <- schemesLocationsFuture
      availableRegions <- frameworkRegionsRepository.getFrameworksByRegionFilteredByQualification(CandidateHighestQualification.from(gd))
    } yield {
      ApplicationValidator(gd, ad, sl, availableRegions).validate match {
        case true => for {
          events <- submit(applicationId, cd.email, gd.preferredName)
          _ <- eventService.handle(events)
        } yield {
          Ok
        }
        case false => Future.successful(BadRequest)
      }
    }

    result flatMap identity
  }

  private def submit(applicationId: String, email: String, preferredName: String): Future[Events] = {
    // Usually events are created in Service layer. Due to lack of Service layer for submit, they are created here
    appRepository.submit(applicationId) map { _ =>
      DataStoreEvents.ApplicationSubmitted(applicationId) ::
      EmailEvents.ApplicationSubmitted(email, preferredName) ::
      AuditEvents.ApplicationSubmitted(applicationId) ::
      Nil
    }
  }
}
