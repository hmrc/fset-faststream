/*
 * Copyright 2017 HM Revenue & Customs
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

import model.Candidate
import model.Exceptions.ApplicationNotFound
import model.stc.StcEventTypes.StcEvents
import model.stc.{ AuditEvents, DataStoreEvents, EmailEvents }
import model.persisted.ContactDetails
import model.{ AdjustmentDetail, Adjustments, AdjustmentsComment }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import services.stc.{ StcEventService, EventSink }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object AdjustmentsManagementService extends AdjustmentsManagementService {
  val appRepository = applicationRepository
  val eventService = StcEventService
  val cdRepository = faststreamContactDetailsRepository
}

trait AdjustmentsManagementService extends EventSink {
  val appRepository: GeneralApplicationRepository
  val cdRepository: ContactDetailsRepository

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
        } yield {
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
