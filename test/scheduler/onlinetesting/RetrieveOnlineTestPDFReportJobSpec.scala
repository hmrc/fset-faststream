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

package scheduler.onlinetesting

import model.OnlineTestCommands.OnlineTestApplicationWithCubiksUser
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.WithApplication
import services.onlinetesting.{ OnlineTestRetrievePDFReportService, OnlineTestService }
import testkit.ExtendedTimeout

import scala.concurrent.{ ExecutionContext, Future }

class RetrieveOnlineTestPDFReportJobSpec extends PlaySpec with MockitoSugar with ScalaFutures with ExtendedTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "retrieve online test PDF report job" should {

    "complete successfully when there is no application ready for pdf report retrieval" in new TestFixture {
      when(otRetrievePDFReportServiceMock.nextApplicationReadyForPDFReportRetrieving()).thenReturn(Future.successful(None))
      Job.tryExecute().futureValue mustBe (())
    }

    "complete successfully when there is one application ready for pdf report retrieval" in new TestFixture {
      when(otRetrievePDFReportServiceMock.nextApplicationReadyForPDFReportRetrieving()).thenReturn(Future.successful(Some(application)))
      when(otRetrievePDFReportServiceMock.retrievePDFReport(eqTo(application), any())).thenReturn(Future.successful(()))
      Job.tryExecute().futureValue mustBe (())
      verify(otRetrievePDFReportServiceMock, times(1)).retrievePDFReport(eqTo(application), any())
    }

    "fail when there is an exception getting next application ready for pdf report retrieval" in new TestFixture {
      when(otRetrievePDFReportServiceMock.nextApplicationReadyForPDFReportRetrieving()).thenReturn(Future.failed(new Exception))
      Job.tryExecute().failed.futureValue mustBe an[Exception]
    }
  }

  trait TestFixture extends WithApplication {
    val otRetrievePDFReportServiceMock = mock[OnlineTestRetrievePDFReportService]
    val otServiceMock = mock[OnlineTestService]

    object Job extends RetrieveOnlineTestPDFReportJob {
      override val otRetrievePDFReportService = otRetrievePDFReportServiceMock
      override val otService = otServiceMock

    }

    val application = OnlineTestApplicationWithCubiksUser("appId1", "userId1", 2)
  }
}
