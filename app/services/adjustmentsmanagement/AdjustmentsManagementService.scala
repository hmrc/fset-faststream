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

package services.adjustmentsmanagement

import model.{ AdjustmentDetail, Adjustments, AdjustmentsComment }
import model.Exceptions.ApplicationNotFound
import model.events.{ AuditEvents, DataStoreEvents, EmailEvents }
import model.exchange.AssistanceDetailsExchange
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import services.events.{ EventService, EventSink }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AdjustmentsManagementService extends AdjustmentsManagementService {
  val appRepository = applicationRepository
  val eventService = EventService
  val cdRepository = faststreamContactDetailsRepository
}

trait AdjustmentsManagementService extends EventSink {
  val appRepository: GeneralApplicationRepository
  val cdRepository: ContactDetailsRepository

  def confirmAdjustment(applicationId: String, adjustmentInformation: Adjustments)
                       (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    val standardEventList = DataStoreEvents.ManageAdjustmentsUpdated(applicationId) ::
      AuditEvents.AdjustmentsConfirmed(Map("applicationId" -> applicationId, "adjustments" -> adjustmentInformation.toString)) ::
      Nil

    def toEmailString(header: String, adjustmentDetail: Option[AdjustmentDetail]): String = {

      def mkString(ad: Option[AdjustmentDetail]): Option[String] =
        ad.map(e => List(e.timeNeeded.map( tn => s"$tn% extra time"), e.invigilatedInfo, e.otherInfo).flatten.mkString(", "))

      mkString(adjustmentDetail) match {
        case Some(txt) if !txt.isEmpty => s"$header $txt"
        case _ => ""
      }
    }

    appRepository.find(applicationId).flatMap {
      case Some(candidate) =>
        cdRepository.find(candidate.userId).flatMap { cd =>
          eventSink {
            appRepository.confirmAdjustments(applicationId, adjustmentInformation).map { _ =>
              adjustmentInformation.adjustments match {
                case Some(list) if list.nonEmpty => EmailEvents.AdjustmentsConfirmed(cd.email,
                  candidate.preferredName.getOrElse(candidate.firstName.getOrElse("")),
                  toEmailString("E-tray:", adjustmentInformation.etray),
                  toEmailString("Video interview:", adjustmentInformation.video)) :: standardEventList
                case _ => standardEventList
              }
            }
          }
        }
      case None => throw ApplicationNotFound(applicationId)
    }.map(_ => ())
  }

  def find(applicationId: String): Future[Option[Adjustments]] = {
    appRepository.findAdjustments(applicationId)
  }

  def saveAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment)
                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    eventSink {
      appRepository.saveAdjustmentsComment(applicationId, adjustmentsComment).map { _ =>
        DataStoreEvents.AdjustmentsCommentUpdated(applicationId) ::
        AuditEvents.AdjustmentsCommentUpdated(Map("applicationId" -> applicationId, "adjustmentsComment" -> adjustmentsComment.toString)) ::
          Nil
      }
    }
  }

  def findAdjustmentsComment(applicationId: String): Future[Option[AdjustmentsComment]] = {
    appRepository.findAdjustmentsComment(applicationId)
  }
}
