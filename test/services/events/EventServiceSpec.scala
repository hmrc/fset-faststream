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

package services.events

import model.events.{ AuditEvent, DataStoreEvent, EmailEvent }
import org.scalatest.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.RequestHeader
import services.events.handler.{ AuditEventHandler, DataStoreEventHandler, EmailEventHandler }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait EventServiceSpec extends PlaySpec with Matchers with MockitoSugar {

  val dataStoreEventHandlerMock = mock[DataStoreEventHandler]
  val auditEventHandlerMock = mock[AuditEventHandler]
  val emailEventHandlerMock = mock[EmailEventHandler]

  val eventService = new EventService {
    val dataStoreEventHandler = dataStoreEventHandlerMock
    val auditEventHandler = auditEventHandlerMock
    val emailEventHandler = emailEventHandlerMock
  }

  when(dataStoreEventHandlerMock.handle(any[DataStoreEvent])(any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))
  when(auditEventHandlerMock.handle(any[AuditEvent])(any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))
  when(emailEventHandlerMock.handle(any[EmailEvent])(any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

  def verifyDataStoreEvents(n: Int): Unit =
    verify(dataStoreEventHandlerMock, times(n)).handle(any[DataStoreEvent])(any[HeaderCarrier], any[RequestHeader])
  def verifyAuditEvents(n: Int): Unit = verify(auditEventHandlerMock, times(n)).handle(any[AuditEvent])(any[HeaderCarrier], any[RequestHeader])
  def verifyEmailEvents(n: Int): Unit = verify(emailEventHandlerMock, times(n)).handle(any[EmailEvent])(any[HeaderCarrier], any[RequestHeader])
}
