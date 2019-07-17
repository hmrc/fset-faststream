/*
 * Copyright 2019 HM Revenue & Customs
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

package services.adjustmentsmanagement

import model._
import model.Exceptions.ApplicationNotFound
import model.stc.StcEventTypes.StcEvents
import model.stc.{ AuditEvents, DataStoreEvents, EmailEvents }
import model.persisted.ContactDetails
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import services.scheme.SchemePreferencesService
import services.sift.ApplicationSiftService
import services.stc.{ EventSink, StcEventService }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object AdjustmentsManagementService extends AdjustmentsManagementService {
  override val appRepository = applicationRepository
  override val eventService = StcEventService
  override val cdRepository = faststreamContactDetailsRepository
  override val schemePreferencesService = SchemePreferencesService
  override val schemesRepository = SchemeYamlRepository
  override val applicationSiftService = ApplicationSiftService
}

trait AdjustmentsManagementService extends EventSink {
  val appRepository: GeneralApplicationRepository
  val cdRepository: ContactDetailsRepository
  val schemePreferencesService: SchemePreferencesService
  val schemesRepository: SchemeRepository
  val applicationSiftService: ApplicationSiftService

  val intro = "Adjustments management service"

  private def progressToSiftOrFSAC(applicationId: String, adjustmentInformation: Adjustments)(implicit hc: HeaderCarrier): Future[Unit] = {

    def progressCandidateToSift(schemes: SelectedSchemes): Future[Unit] = {
      for {
        _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SIFT_ENTERED)
        _ <- applicationSiftService.saveSiftExpiryDate(applicationId)
        _ <- applicationSiftService.sendSiftEnteredNotification(applicationId)
      } yield ()
    }

    (for {
      selectedSchemes <- schemePreferencesService.find(applicationId)
    } yield {
      val hasSiftableScheme = schemesRepository.siftableSchemeIds.intersect(selectedSchemes.schemes).nonEmpty
      if (hasSiftableScheme) {
        val hasSiftNumericSchemes = schemesRepository.numericTestSiftRequirementSchemeIds.intersect(selectedSchemes.schemes).nonEmpty
        if(hasSiftNumericSchemes) {
          val timeAdjustmentsSpecified = adjustmentInformation.etray.exists(_.timeNeeded.isDefined)

          if(timeAdjustmentsSpecified) {
            Logger.info(s"$intro - candidate $applicationId has sift numeric schemes and adjustments, which " +
              s"have been applied so moving to ${ProgressStatuses.SIFT_ENTERED}")
            progressCandidateToSift(selectedSchemes).map(_ => ())
          } else {
            Logger.info(s"$intro - candidate $applicationId has sift numeric schemes but no time adjustments " +
              s"so not progressing to ${ProgressStatuses.SIFT_ENTERED}")
            Future.successful(())
          }
        } else {
          Logger.info(s"$intro - candidate $applicationId has siftable schemes but no numeric schemes so moving " +
            s"to ${ProgressStatuses.SIFT_ENTERED}")
          progressCandidateToSift(selectedSchemes).map(_ => ())
        }
      } else {
        Logger.info(s"$intro - candidate $applicationId has no siftable schemes so moving " +
          s"to ${ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION}")
        appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION).map(_ => ())
      }
    }).flatMap(identity)
  }

  def confirmAdjustment(applicationId: String, adjustmentInformation: Adjustments)
                       (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    val adjustmentsDataStoreAndAuditEvents: StcEvents =
      DataStoreEvents.ManageAdjustmentsUpdated(applicationId) ::
      AuditEvents.AdjustmentsConfirmed(Map("applicationId" -> applicationId, "adjustments" -> adjustmentInformation.toString)) ::
      Nil

    appRepository.find(applicationId).flatMap {
      case Some(candidate) => eventSink {
        for {
          cd <- cdRepository.find(candidate.userId)
          previousAdjustments <- appRepository.findAdjustments(applicationId)
          _ <- appRepository.confirmAdjustments(applicationId, adjustmentInformation)
          applicationStatus <- appRepository.findStatus(applicationId)
        } yield {

          if(ApplicationStatus.withName(applicationStatus.status) == ApplicationStatus.FAST_PASS_ACCEPTED) {
            Logger.info(s"$intro - candidate $applicationId is in ${ApplicationStatus.FAST_PASS_ACCEPTED} so will check if we " +
              "should move to SIFT or FSAC")
            progressToSiftOrFSAC(applicationId, adjustmentInformation).map(_ => ())
          } else {
            Logger.info(s"$intro - candidate $applicationId is not in ${ApplicationStatus.FAST_PASS_ACCEPTED} so will skip the " +
              s"check to move to SIFT or FSAC. Candidate is in ${applicationStatus.status}")
          }

          val hasNewAdjustments = adjustmentInformation.adjustments.exists(_.nonEmpty)
          val hasPreviousAdjustments = previousAdjustments.flatMap(_.adjustmentsConfirmed).getOrElse(false)

          val events = if (hasNewAdjustments || hasPreviousAdjustments) {
            createEmailEvents(candidate, adjustmentInformation, hasPreviousAdjustments, cd) :: adjustmentsDataStoreAndAuditEvents
          } else {
            adjustmentsDataStoreAndAuditEvents
          }
          events
        }
      }
      case None => throw ApplicationNotFound(applicationId)
    }.map(_ => ())
  }

  private def createEmailEvents(candidate: Candidate, adjustmentInformation: Adjustments,
                                hasPreviousAdjustments: Boolean, cd: ContactDetails) = {
    if (hasPreviousAdjustments) {
      EmailEvents.AdjustmentsChanged(
        cd.email,
        candidate.preferredName.getOrElse(candidate.firstName.getOrElse("")),
        toEmailString("E-tray:", adjustmentInformation.etray),
        toEmailString("Video interview:", adjustmentInformation.video)
      )
    } else {
      EmailEvents.AdjustmentsConfirmed(
        cd.email,
        candidate.preferredName.getOrElse(candidate.firstName.getOrElse("")),
        toEmailString("E-tray:", adjustmentInformation.etray),
        toEmailString("Video interview:", adjustmentInformation.video)
      )
    }
  }

  private def toEmailString(header: String, adjustmentDetail: Option[AdjustmentDetail]): String = {

    def mkString(ad: Option[AdjustmentDetail]): Option[String] =
      ad.map(e => List(e.timeNeeded.map( tn => s"$tn% extra time"), e.invigilatedInfo, e.otherInfo).flatten.mkString(", "))

    mkString(adjustmentDetail) match {
      case Some(txt) if !txt.isEmpty => s"$header $txt"
      case _ => ""
    }
  }

  def find(applicationId: String): Future[Option[Adjustments]] = {
    appRepository.findAdjustments(applicationId)
  }

  def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment)
                              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    eventSink {
      appRepository.updateAdjustmentsComment(applicationId, adjustmentsComment).map { _ =>
        DataStoreEvents.AdjustmentsCommentUpdated(applicationId) ::
        AuditEvents.AdjustmentsCommentUpdated(Map("applicationId" -> applicationId, "adjustmentsComment" -> adjustmentsComment.toString)) ::
          Nil
      }
    }
  }

  def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment] = {
    appRepository.findAdjustmentsComment(applicationId)
  }

  def removeAdjustmentsComment(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    eventSink {
      appRepository.removeAdjustmentsComment(applicationId).map { _ =>
        DataStoreEvents.AdjustmentsCommentRemoved(applicationId) ::
          AuditEvents.AdjustmentsCommentRemoved(Map("applicationId" -> applicationId)) ::
          Nil
      }
    }
  }
}
