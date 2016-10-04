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

import model.command.WithdrawApplication
import model.events.EventTypes.Events
import model.events.{ AuditEvents, DataStoreEvents }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import services.AuditService
import services.events.{ EventService, EventSink }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ApplicationService extends ApplicationService {
  val appRepository = applicationRepository
  val eventService = EventService
}

trait ApplicationService extends EventSink {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val appRepository: GeneralApplicationRepository
  val eventService: EventService

  def withdraw(applicationId: String, withdrawRequest: WithdrawApplication)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    appRepository.withdraw(applicationId, withdrawRequest) map { _ =>
      DataStoreEvents.ApplicationWithdrawn(applicationId, withdrawRequest.withdrawer) ::
      AuditEvents.ApplicationWithdrawn(Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawRequest.toString)) ::
      Nil
    }
  }
}
