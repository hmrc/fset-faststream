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

package services.stc

import model.stc.StcEventTypes.StcEvents
import play.api.mvc.RequestHeader
import services.stc.handler.{ AuditEventHandler, DataStoreEventHandler, EmailEventHandler }
import testkit.MockitoSugar
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait StcEventServiceMock extends MockitoSugar {

  val dataStoreEventHandlerMock = mock[DataStoreEventHandler]
  val auditEventHandlerMock = mock[AuditEventHandler]
  val emailEventHandlerMock = mock[EmailEventHandler]

  val eventServiceStub = new StcEventService {
    var cache = List.empty[StcEvents]

    override protected[stc] def handle(events: StcEvents)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
      cache ::= events
      Future.successful(())
    }

    val dataStoreEventHandler = dataStoreEventHandlerMock
    val auditEventHandler = auditEventHandlerMock
    val emailEventHandler = emailEventHandlerMock


  }
}
