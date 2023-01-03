/*
 * Copyright 2023 HM Revenue & Customs
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

package services

import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import testkit.UnitSpec
import uk.gov.hmrc.play.audit.model.{ Audit, DataEvent }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

class AuditServiceSpec extends UnitSpec {

  "Log Event" should {
    "log event without details" in new TestFixture {
      auditService.logEvent("SomeEvent")

      verify(auditMockResponse, atLeastOnce()).apply(eventCaptor.capture())
      val actualDataEvent = eventCaptor.getValue

      actualDataEvent.auditSource mustBe "appName"
      actualDataEvent.auditType mustBe "TxSucceeded"
      actualDataEvent.tags("transactionName") mustBe "SomeEvent"
      actualDataEvent.tags("path") mustBe "some/path"
    }

    "log event with details" in new TestFixture {
      auditService.logEvent("SomeEvent", Map("moreInfo" -> "moreDetails"))

      verify(auditMockResponse, atLeastOnce()).apply(eventCaptor.capture())
      val actualDataEvent = eventCaptor.getValue

      actualDataEvent.auditSource mustBe "appName"
      actualDataEvent.auditType mustBe "TxSucceeded"
      actualDataEvent.tags("transactionName") mustBe "SomeEvent"
      actualDataEvent.tags("path") mustBe "some/path"
      actualDataEvent.detail("moreInfo") mustBe "moreDetails"
    }

    "log event without request" in new TestFixture {
      auditService.logEventNoRequest("SomeEvent", Map("moreInfo" -> "moreDetails"))

      verify(auditMockResponse, atLeastOnce()).apply(eventCaptor.capture())
      val actualDataEvent = eventCaptor.getValue

      actualDataEvent.auditSource mustBe "appName"
      actualDataEvent.auditType mustBe "TxSucceeded"
      actualDataEvent.tags("transactionName") mustBe "SomeEvent"
      actualDataEvent.tags must not contain "path"
      actualDataEvent.detail("moreInfo") mustBe "moreDetails"
    }
  }

  trait TestFixture {
    val auditMock = mock[Audit]
    val auditConnectorMock = mock[AuditConnector]
    val auditMockResponse = mock[DataEvent => Unit]
    val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val rh: RequestHeader = FakeRequest("GET", "some/path")

    when(auditMock.sendDataEvent).thenReturn(auditMockResponse)

    val auditService = new AuditService("appName", auditConnectorMock) {
      override val auditFacade: Audit = auditMock
    }
  }
}
