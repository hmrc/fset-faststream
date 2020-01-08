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

package services.stc

import connectors.AuthProviderClient
import model.exchange.SimpleTokenResponse
import model.stc._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.MustMatchers
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.RequestHeader
import services.stc.handler._
import testkit.UnitSpec
import testkit.MockitoImplicits._

import scala.collection.JavaConversions._
import uk.gov.hmrc.http.HeaderCarrier

class StcEventServiceSpec extends UnitSpec with StcEventServiceFixture {

  "Event service" should {
    "consume lists of events properly" in {
      implicit val hc = mock[HeaderCarrier]
      implicit val rh = mock[RequestHeader]

      eventServiceMock.handle(model.stc.AuditEvents.ApplicationSubmitted("appId"))
      verifyAuditEvents(1)
    }
  }
}

trait StcEventServiceFixture extends MockitoSugar with MustMatchers {

  val dataStoreEventHandlerMock = mock[DataStoreEventHandler]
  val auditEventHandlerMock = mock[AuditEventHandler]
  val emailEventHandlerMock = mock[EmailEventHandler]

  val eventServiceMock = new StcEventService {
    val dataStoreEventHandler = dataStoreEventHandlerMock
    val auditEventHandler = auditEventHandlerMock
    val emailEventHandler = emailEventHandlerMock
  }

  val authProviderClientMock = mock[AuthProviderClient]

  when(dataStoreEventHandlerMock.handle(any[DataStoreEvent])(any[HeaderCarrier], any[RequestHeader])).thenReturnAsync()
  when(auditEventHandlerMock.handle(any[AuditEvent])(any[HeaderCarrier], any[RequestHeader])).thenReturnAsync()
  when(emailEventHandlerMock.handle(any[EmailEvent])(any[HeaderCarrier], any[RequestHeader])).thenReturnAsync()
  when(authProviderClientMock.generateAccessCode(any[HeaderCarrier])).thenReturnAsync(SimpleTokenResponse("accessCode"))

  def verifyDataStoreEvents(n: Int): Unit =
    verify(dataStoreEventHandlerMock, times(n)).handle(any[DataStoreEvent])(any[HeaderCarrier], any[RequestHeader])

  def verifyDataStoreEvents(n: Int, eventName: String): Unit = {
    val eventCaptor = ArgumentCaptor.forClass(classOf[DataStoreEvent])
    verify(dataStoreEventHandlerMock, times(n)).handle(eventCaptor.capture)(any[HeaderCarrier], any[RequestHeader])
    assert(eventCaptor.getAllValues.toList.forall(_.eventName == eventName))
  }

  def verifyDataStoreEvent(eventName: String): Unit = {
    val eventCaptor = ArgumentCaptor.forClass(classOf[DataStoreEvent])
    verify(dataStoreEventHandlerMock).handle(eventCaptor.capture)(any[HeaderCarrier], any[RequestHeader])
    eventCaptor.getAllValues.size() mustBe 1
    eventCaptor.getAllValues.map(_.eventName).headOption mustBe Some(eventName)
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

  def verifyAuditEvent(eventName: String): Unit = {
    val eventCaptor = ArgumentCaptor.forClass(classOf[AuditEvent])
    verify(auditEventHandlerMock).handle(eventCaptor.capture)(any[HeaderCarrier], any[RequestHeader])
    eventCaptor.getAllValues.size() mustBe 1
    eventCaptor.getAllValues.map(_.eventName).headOption mustBe Some(eventName)
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
    assert(eventCaptor.getAllValues.toList.forall(x => x.eventName == eventName))
  }

  def verifyEmailEvent(eventName: String): Unit = {
    val eventCaptor = ArgumentCaptor.forClass(classOf[EmailEvent])
    verify(emailEventHandlerMock).handle(eventCaptor.capture)(any[HeaderCarrier], any[RequestHeader])
    eventCaptor.getAllValues.size() mustBe 1
    eventCaptor.getAllValues.map(_.eventName).headOption mustBe Some(eventName)
  }
}
