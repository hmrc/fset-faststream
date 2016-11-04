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

package services.application

import connectors.{ CSREmailClient, EmailClient }
import model.command.WithdrawApplication
import model.events.EventTypes.Events
import model.events.{ AuditEvents, DataStoreEvents, EmailEvents }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.personaldetails.PersonalDetailsRepository
import contactdetails.ContactDetailsRepository
import model.Commands.{ AdjustmentDetail, AdjustmentManagement }
import model.Exceptions.ApplicationNotFound
import model.persisted.{ ContactDetails, PersonalDetails }
import services.AuditService
import services.events.{ EventService, EventSink }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ApplicationService extends ApplicationService {
  val appRepository = applicationRepository
  val eventService = EventService
  val pdRepository = faststreamPersonalDetailsRepository
  val cdRepository = faststreamContactDetailsRepository
}

trait ApplicationService extends EventSink {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val appRepository: GeneralApplicationRepository
  val pdRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository

  val Candidate_Role = "Candidate"

  def withdraw(applicationId: String, withdrawRequest: WithdrawApplication)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    appRepository.find(applicationId).flatMap{
      case Some(candidate) =>
        cdRepository.find(candidate.userId).flatMap{ cd =>
          eventSink {
            appRepository.withdraw(applicationId, withdrawRequest).map{ _ =>
              val commonEventList =
                  DataStoreEvents.ApplicationWithdrawn(applicationId, withdrawRequest.withdrawer) ::
                  AuditEvents.ApplicationWithdrawn(Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawRequest.toString)) ::
                  Nil
              withdrawRequest.withdrawer match {
                case Candidate_Role => commonEventList
                case _ => EmailEvents.ApplicationWithdrawn(cd.email,
                  candidate.preferredName.getOrElse(candidate.firstName.getOrElse(""))) :: commonEventList
              }
            }
          }
        }
      case None => throw ApplicationNotFound(applicationId)
    }.map(_ => ())
  }

  def confirmAdjustment(applicationId: String, adjustmentInformation: AdjustmentManagement)
                       (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    val standardEventList = DataStoreEvents.ManageAdjustmentsUpdated(applicationId) ::
      AuditEvents.AdjustmentsConfirmed(Map("applicationId" -> applicationId, "adjustments" -> adjustmentInformation.toString)) ::
      Nil

    def toEmailString(header: String, adjustmentDetail: Option[AdjustmentDetail]): String ={

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
            appRepository.confirmAdjustment(applicationId, adjustmentInformation).map{ _ =>
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

}
