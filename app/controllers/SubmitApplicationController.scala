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

import connectors.{CSREmailClient, EmailClient}
import model.ApplicationValidator
import play.api.mvc.Action
import repositories.FrameworkRepository.CandidateHighestQualification
import repositories._
import repositories.application.{GeneralApplicationRepository, PersonalDetailsRepository}
import repositories.assistancedetails.AssistanceDetailsRepository
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaz.Scalaz._

object SubmitApplicationController extends SubmitApplicationController {
  override val pdRepository: PersonalDetailsRepository = personalDetailsRepository
  override val adRepository: AssistanceDetailsRepository = faststreamAssistanceDetailsRepository
  override val cdRepository = contactDetailsRepository
  override val frameworkPrefRepository: FrameworkPreferenceMongoRepository = frameworkPreferenceRepository
  override val frameworkRegionsRepository: FrameworkRepository = frameworkRepository
  override val appRepository: GeneralApplicationRepository = applicationRepository
  override val emailClient = CSREmailClient
  override val auditService = AuditService
}

trait SubmitApplicationController extends BaseController {

  val pdRepository: PersonalDetailsRepository
  val adRepository: AssistanceDetailsRepository
  val cdRepository: ContactDetailsRepository
  val frameworkPrefRepository: FrameworkPreferenceRepository
  val frameworkRegionsRepository: FrameworkRepository
  val appRepository: GeneralApplicationRepository
  val emailClient: EmailClient
  val auditService: AuditService

  def submitApplication(userId: String, applicationId: String) = Action.async { implicit request =>
    val generalDetailsFuture = pdRepository.find(applicationId)
    val assistanceDetailsFuture = adRepository.find(applicationId)
    val contactDetailsFuture = cdRepository.find(userId)
    val schemesLocationsFuture = frameworkPrefRepository.tryGetPreferences(applicationId)

    (for {
      gd <- generalDetailsFuture
      ad <- assistanceDetailsFuture
      cd <- contactDetailsFuture
    } yield {
      schemesLocationsFuture.flatMap { sl =>
        val criteriaMet = CandidateHighestQualification.from(gd)
        frameworkRegionsRepository.getFrameworksByRegionFilteredByQualification(criteriaMet).map { availableRegions =>
          ApplicationValidator(gd, ad, sl, availableRegions).validate match {
            case true => appRepository.submit(applicationId).map { _ =>
              emailClient.sendApplicationSubmittedConfirmation(cd.email, gd.preferredName).map { _ =>
                auditService.logEvent("ApplicationSubmitted")
                Ok
              }
            }.join
            case false => Future.successful(BadRequest)
          }
        }
      }
    }).join.join
  }

}
