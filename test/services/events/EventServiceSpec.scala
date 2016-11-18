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

import connectors.AuthProviderClient
import model.events.{ AuditEvent, DataStoreEvent, EmailEvent }
import model.exchange.SimpleTokenResponse
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.mvc.RequestHeader
import services.events.handler.{ AuditEventHandler, DataStoreEventHandler, EmailEventHandler }
import testkit.UnitSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.collection.JavaConversions._
import scala.concurrent.Future

class EventServiceSpec extends UnitSpec with EventServiceFixture {

  "Event service" should {
    "consume lists of events properly" in {
      implicit val hc = mock[HeaderCarrier]
      implicit val rh = mock[RequestHeader]

      eventServiceMock.handle(model.events.AuditEvents.ApplicationSubmitted("appId"))
      verifyAuditEvents(1)
    }
  }
}

trait EventServiceFixture extends MockitoSugar {

  val dataStoreEventHandlerMock = mock[DataStoreEventHandler]
  val auditEventHandlerMock = mock[AuditEventHandler]
  val emailEventHandlerMock = mock[EmailEventHandler]

  val eventServiceMock = new EventService {
    val dataStoreEventHandler = dataStoreEventHandlerMock
    val auditEventHandler = auditEventHandlerMock
    val emailEventHandler = emailEventHandlerMock
  }

  val authProviderClientMock = mock[AuthProviderClient]

  when(dataStoreEventHandlerMock.handle(any[DataStoreEvent])(any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))
  when(auditEventHandlerMock.handle(any[AuditEvent])(any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))
  when(emailEventHandlerMock.handle(any[EmailEvent])(any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))
  when(authProviderClientMock.generateAccessCode(any[HeaderCarrier])).thenReturn(Future.successful(SimpleTokenResponse("accessCode")))

  def verifyDataStoreEvents(n: Int): Unit =
    verify(dataStoreEventHandlerMock, times(n)).handle(any[DataStoreEvent])(any[HeaderCarrier], any[RequestHeader])

  def verifyDataStoreEvents(n: Int, eventName: String): Unit = {
    val eventCaptor = ArgumentCaptor.forClass(classOf[DataStoreEvent])
    verify(dataStoreEventHandlerMock, times(n)).handle(eventCaptor.capture)(any[HeaderCarrier], any[RequestHeader])
    assert(eventCaptor.getAllValues.toList.forall(_.eventName == eventName))
  }

  def verifyDataStoreEvents(n: Int, eventNames: List[String]): Unit = {
    val eventCaptor = ArgumentCaptor.forClass(classOf[DataStoreEvent])
    verify(dataStoreEventHandlerMock, times(n)).handle(eventCaptor.capture)(any[HeaderCarrier], any[RequestHeader])
    assert(eventNames.forall(eventName => eventCaptor.getAllValues.toList.exists(_.eventName == eventName)))
  }

  def verifyAuditEvents(n: Int): Unit =
    verify(auditEventHandlerMock, times(n)).handle(any[AuditEvent])(any[HeaderCarrier], any[RequestHeader])

  def verifyAuditEvents(n: Int, eventName: String): Unit = {
    val eventCaptor = ArgumentCaptor.forClass(classOf[AuditEvent])
    verify(auditEventHandlerMock, times(n)).handle(eventCaptor.capture)(any[HeaderCarrier], any[RequestHeader])
    assert(eventCaptor.getAllValues.toList.forall(_.eventName == eventName))
  }

  def verifyAuditEvents(n: Int, eventNames: List[String]): Unit = {
    val eventCaptor = ArgumentCaptor.forClass(classOf[AuditEvent])
    verify(auditEventHandlerMock, times(n)).handle(eventCaptor.capture)(any[HeaderCarrier], any[RequestHeader])
    assert(eventNames.forall(eventName => eventCaptor.getAllValues.toList.exists(_.eventName == eventName)))
  }

  def verifyEmailEvents(n: Int): Unit =
    verify(emailEventHandlerMock, times(n)).handle(any[EmailEvent])(any[HeaderCarrier], any[RequestHeader])

  def verifyEmailEvents(n: Int, eventName: String): Unit = {
    val eventCaptor = ArgumentCaptor.forClass(classOf[EmailEvent])
    verify(emailEventHandlerMock, times(n)).handle(eventCaptor.capture)(any[HeaderCarrier], any[RequestHeader])
    assert(eventCaptor.getAllValues.toList.forall(_.eventName == eventName))
  }
}
