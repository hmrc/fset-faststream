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

package services

import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import testkit.UnitSpec
import uk.gov.hmrc.play.audit.model.{ Audit, DataEvent }
import uk.gov.hmrc.http.HeaderCarrier

class AuditServiceSpec extends UnitSpec {
  val auditMock = mock[Audit]
  val auditMockResponse = mock[(DataEvent) => Unit]
  val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])

  "Log Event" should {
    "log event without details" in new TestableAuditService {
      logEvent("SomeEvent")

      verify(auditMockResponse, atLeastOnce()).apply(eventCaptor.capture())
      val actualDataEvent = eventCaptor.getValue

      actualDataEvent.auditSource must be("appName")
      actualDataEvent.auditType must be("TxSucceeded")
      actualDataEvent.tags("transactionName") must be("SomeEvent")
      actualDataEvent.tags("path") must be("some/path")
    }

    "log event with details" in new TestableAuditService {
      logEvent("SomeEvent", Map("moreInfo" -> "moreDetails"))

      verify(auditMockResponse, atLeastOnce()).apply(eventCaptor.capture())
      val actualDataEvent = eventCaptor.getValue

      actualDataEvent.auditSource must be("appName")
      actualDataEvent.auditType must be("TxSucceeded")
      actualDataEvent.tags("transactionName") must be("SomeEvent")
      actualDataEvent.tags("path") must be("some/path")
      actualDataEvent.detail("moreInfo") must be("moreDetails")
    }

    "log event without request" in new TestableAuditService {
      logEventNoRequest("SomeEvent", Map("moreInfo" -> "moreDetails"))

      verify(auditMockResponse, atLeastOnce()).apply(eventCaptor.capture())
      val actualDataEvent = eventCaptor.getValue

      actualDataEvent.auditSource must be("appName")
      actualDataEvent.auditType must be("TxSucceeded")
      actualDataEvent.tags("transactionName") must be("SomeEvent")
      actualDataEvent.tags must not contain "path"
      actualDataEvent.detail("moreInfo") must be("moreDetails")
    }
  }

  class TestableAuditService extends AuditService {
    private[services] val appName = "appName"
    private[services] val auditFacade: Audit = auditMock

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val rh: RequestHeader = FakeRequest("GET", "some/path")

    when(auditMock.sendDataEvent).thenReturn(auditMockResponse)
  }
}
