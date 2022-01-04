/*
 * Copyright 2022 HM Revenue & Customs
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

package services.fastpass

import com.google.inject.name.Named
import connectors.OnlineTestEmailClient
import javax.inject.{ Inject, Singleton }
import model._
import model.persisted.SchemeEvaluationResult
import model.stc.AuditEvents.{ FastPassUserAccepted, FastPassUserAcceptedEmailSent, FastPassUserRejected }
import model.stc.DataStoreEvents.{ ApplicationReadyForExport, FastPassApproved, FastPassRejected }
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import services.adjustmentsmanagement.AdjustmentsManagementService
import services.personaldetails.PersonalDetailsService
import services.scheme.SchemePreferencesService
import services.sift.ApplicationSiftService
import services.stc.{ EventSink, StcEventService }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class FastPassService @Inject() (appRepo: GeneralApplicationRepository,
                                 personalDetailsService: PersonalDetailsService,
                                 val eventService: StcEventService,
                                 @Named("CSREmailClient") emailClient: OnlineTestEmailClient,
                                 cdRepository: ContactDetailsRepository,
                                 csedRepository: CivilServiceExperienceDetailsRepository,
                                 schemePreferencesService: SchemePreferencesService,
                                 schemesRepository: SchemeRepository,
                                 applicationSiftService: ApplicationSiftService,
                                 adjustmentsManagementService: AdjustmentsManagementService,
                                 assistanceDetailsRepository: AssistanceDetailsRepository
                                ) extends EventSink with CurrentSchemeStatusHelper with Logging {

  val fastPassDetails = CivilServiceExperienceDetails(
    applicable = true,
    fastPassReceived = Some(true),
    fastPassAccepted = Some(true),
    certificateNumber = Some("0000000")
  )

  val acceptedTemplate = "fset_faststream_app_online_fast-pass_accepted"

  def processFastPassCandidate(userId: String, applicationId: String, accepted: Boolean, actionTriggeredBy: String)
                              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[(String, String)] = {

    (for {
      progressResponse <- appRepo.findProgress(applicationId)
    } yield {
      if (progressResponse.submitted) {
        if (accepted) {
          acceptFastPassCandidate(userId, applicationId, actionTriggeredBy)
        } else {
          rejectFastPassCandidate(userId, applicationId, actionTriggeredBy)
        }
      } else {
        throw new IllegalStateException(s"Candidate $applicationId cannot have their fast pass accepted/rejected because their " +
          "application has not been submitted")
      }
    }).flatMap(identity)
  }

  def promoteToFastPassCandidate(applicationId: String, actionTriggeredBy: String)
                                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    val eventMap = Map("createdBy" -> actionTriggeredBy, "applicationId" -> applicationId)
    for {
      _ <- csedRepository.update(applicationId, fastPassDetails)
      _ <- appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.FAST_PASS_ACCEPTED)
      _ <- eventSink(model.stc.AuditEvents.ApplicationReadyForExport(eventMap) :: ApplicationReadyForExport(applicationId) :: Nil)
      _ <- eventSink(FastPassUserAccepted(eventMap) :: FastPassApproved(applicationId, actionTriggeredBy) :: Nil)

    } yield ()
  }

  //scalastyle:off method.length
  private def autoProgressToSiftOrFSAC(applicationId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    def notifySiftEntered(siftStatus: ProgressStatuses.ProgressStatus): Future[Unit] = {
      if(siftStatus == ProgressStatuses.SIFT_ENTERED) {
        for {
          expiryDate <- applicationSiftService.fetchSiftExpiryDate(applicationId)
          _ <- applicationSiftService.sendSiftEnteredNotification(applicationId, expiryDate)
        } yield ()
      } else {
        Future.successful(())
      }
    }

    def progressCandidate(schemes: SelectedSchemes): Future[Unit] = {
      val siftStatus = applicationSiftService.progressStatusForSiftStage(schemes.schemes)
      appRepo.addProgressStatusAndUpdateAppStatus(applicationId, siftStatus).flatMap { _ =>
        val startExpiry = if (siftStatus == ProgressStatuses.SIFT_ENTERED) {
          applicationSiftService.saveSiftExpiryDate(applicationId)
        } else {
          Future.successful(())
        }
        startExpiry.flatMap(_ => notifySiftEntered(siftStatus).map(_ => ()))
      }
    }

    (for {
      selectedSchemes <- schemePreferencesService.find(applicationId)
    } yield {
      val intro = "fastpass service"
      val hasSiftableScheme = schemesRepository.siftableSchemeIds.intersect(selectedSchemes.schemes).nonEmpty
      if (hasSiftableScheme) {
        val hasSiftNumericSchemes = schemesRepository.numericTestSiftRequirementSchemeIds.intersect(selectedSchemes.schemes).nonEmpty
        if(hasSiftNumericSchemes) {

          (for {
            assistanceDetails <- assistanceDetailsRepository.find(applicationId)
            adjustmentsOpt <- adjustmentsManagementService.find(applicationId)
          } yield {
            val timeAdjustmentsSpecified = adjustmentsOpt.exists(a => a.etray.exists(_.timeNeeded.isDefined))
            val adjustmentDetails = assistanceDetails.needsSupportForOnlineAssessment.getOrElse(false) -> timeAdjustmentsSpecified

            adjustmentDetails match {
              case (false, _) => // Candidate has no adjustments
                logger.info(s"$intro - candidate $applicationId has sift numeric schemes and no adjustments " +
                  s"so moving to ${ProgressStatuses.SIFT_ENTERED}")
                progressCandidate(selectedSchemes)
              case (true, true) => // Candidate has adjustments and they have been applied
                logger.info(s"$intro - candidate $applicationId has sift numeric schemes and adjustments, which " +
                  s"have been applied so moving to ${ProgressStatuses.SIFT_ENTERED}")
                progressCandidate(selectedSchemes)
              case _ => // Everything else eg. has adjustments but they haven't been applied
                logger.info(s"$intro - candidate $applicationId has sift numeric schemes but adjustments are not in a " +
                  s"state to progress to ${ProgressStatuses.SIFT_ENTERED}")
                Future.successful(())
            }
          }).flatMap(identity)

        } else {
          logger.info(s"$intro - candidate $applicationId has siftable schemes but no numeric schemes so moving " +
            s"to ${ProgressStatuses.SIFT_ENTERED}")
          progressCandidate(selectedSchemes)
        }
      } else {
        logger.info(s"$intro - candidate $applicationId has no siftable schemes so moving " +
          s"to ${ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION}")
        appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      }
    }).flatMap(identity)
  }

  def createCurrentSchemeStatus(applicationId: String, selectedSchemes: SelectedSchemes): Future[Unit] = {
    val results = selectedSchemes.schemes.map { schemeId =>
      SchemeEvaluationResult(schemeId, EvaluationResults.Green.toString)
    }
    appRepo.updateCurrentSchemeStatus(applicationId, results)
  }

  private def acceptFastPassCandidate(userId: String, applicationId: String, actionTriggeredBy: String)
                                     (implicit hc: HeaderCarrier, rh: RequestHeader): Future[(String, String)] = {

    val emailFut = cdRepository.find(userId).map(_.email)
    val personalDetailsFut = personalDetailsService.find(applicationId, userId)
    val eventMap = Map("createdBy" -> actionTriggeredBy, "candidate" -> userId)
    for {
      email <- emailFut
      personalDetail <- personalDetailsFut
      _ <- appRepo.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.FAST_PASS_ACCEPTED)
      preferences <- schemePreferencesService.find(applicationId)
      _ <- createCurrentSchemeStatus(applicationId, preferences)
      _ <- eventSink(model.stc.AuditEvents.ApplicationReadyForExport(eventMap) :: ApplicationReadyForExport(applicationId) :: Nil)
      _ <- csedRepository.evaluateFastPassCandidate(applicationId, accepted = true)
      _ <- eventSink(FastPassUserAccepted(eventMap) :: FastPassApproved(applicationId, actionTriggeredBy) :: Nil)
      _ <- emailClient.sendEmailWithName(email, personalDetail.preferredName, acceptedTemplate)
      _ <- autoProgressToSiftOrFSAC(applicationId)
      _ <- eventSink(FastPassUserAcceptedEmailSent(
        Map("email" -> email, "name" -> personalDetail.preferredName, "template" -> acceptedTemplate)) :: Nil)
    } yield (personalDetail.firstName, personalDetail.lastName)
  }

  private def rejectFastPassCandidate(userId: String, applicationId: String, actionTriggeredBy: String)
                                     (implicit hc: HeaderCarrier, rh: RequestHeader): Future[(String, String)] = {

    val eventMap = Map("createdBy" -> actionTriggeredBy, "candidate" -> userId)
    for {
      personalDetail <- personalDetailsService.find(applicationId, userId)
      _ <- csedRepository.evaluateFastPassCandidate(applicationId, accepted = false)
      _ <- eventSink(FastPassUserRejected(eventMap) :: FastPassRejected(applicationId, actionTriggeredBy) :: Nil)
    } yield (personalDetail.firstName, personalDetail.lastName)
  }
}
