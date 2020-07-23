/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import model.ApplicationValidator
import model.stc.{ AuditEvents, DataStoreEvents, EmailEvents }
import play.api.mvc.{ Action, RequestHeader }
import repositories.FrameworkRepository.CandidateHighestQualification
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.stc.{ EventSink, StcEventService }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//object SubmitApplicationController extends SubmitApplicationController {
//  override val pdRepository: PersonalDetailsRepository = personalDetailsRepository
//  override val adRepository: AssistanceDetailsRepository = faststreamAssistanceDetailsRepository
//  override val cdRepository = faststreamContactDetailsRepository
//  override val frameworkPrefRepository: FrameworkPreferenceMongoRepository = frameworkPreferenceRepository
//  override val frameworkRegionsRepository: FrameworkRepository = frameworkRepository
//  override val appRepository: GeneralApplicationRepository = applicationRepository
//  override val eventService: StcEventService = StcEventService
//}

@Singleton
class SubmitApplicationController @Inject() (pdRepository: PersonalDetailsRepository,
                                             adRepository: AssistanceDetailsRepository,
                                             cdRepository: ContactDetailsRepository,
                                             frameworkPrefRepository: FrameworkPreferenceRepository,
                                             frameworkRegionsRepository: FrameworkRepository,
                                             appRepository: GeneralApplicationRepository,
                                             override val eventService: StcEventService
                                            ) extends BaseController with EventSink {

//  val pdRepository: PersonalDetailsRepository
//  val adRepository: AssistanceDetailsRepository
//  val cdRepository: contactdetails.ContactDetailsRepository
//  val frameworkPrefRepository: FrameworkPreferenceRepository
//  val frameworkRegionsRepository: FrameworkRepository
//  val appRepository: GeneralApplicationRepository

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
      if (ApplicationValidator(gd, ad, sl, availableRegions).validate) {
        submit(applicationId, cd.email, gd.preferredName).map(_ => Ok)
      } else {
        Future.successful(BadRequest)
      }
    }

    result flatMap identity
  }

  private def submit(applicationId: String, email: String, preferredName: String)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    // Usually events are created in Service layer. Due to lack of Service layer for submit, they are created here
    appRepository.submit(applicationId) map { _ =>
      DataStoreEvents.ApplicationSubmitted(applicationId) ::
      EmailEvents.ApplicationSubmitted(email, preferredName) ::
      AuditEvents.ApplicationSubmitted(applicationId) ::
      Nil
    }
  }
}
