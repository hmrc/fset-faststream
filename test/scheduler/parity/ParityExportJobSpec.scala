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

package scheduler.parity

import config.ScheduledJobConfig
import model.Exceptions.ConnectorException
import org.joda.time.{ DateTime, DateTimeZone }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.parity.ApplicationReadyForExport
import services.parity.ParityExportService
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ParityExportJobSpec extends UnitWithAppSpec {
  implicit val now: DateTime = DateTime.now().withZone(DateTimeZone.UTC)

  "Scheduler execution" should {
    "export applications ready for export" in new TestFixture {
      val appId = "appId1"
      when(mockParityService.nextApplicationsForExport(any[Int])).thenReturn(Future.successful(
          ApplicationReadyForExport(appId) :: Nil
        ))
      when(mockParityService.exportApplication(any[String])(any[HeaderCarrier](), any[RequestHeader]())).thenReturn(Future.successful(
        ()
      ))

      scheduler.tryExecute().futureValue

      verify(mockParityService, times(1)).nextApplicationsForExport(eqTo(batchSize))
      verify(mockParityService, times(1)).exportApplication(eqTo(appId))(any[HeaderCarrier](), any[RequestHeader]())
    }

    "export all applications even when some of them fail" in new TestFixture {
      val appIds = List("appId1", "appId2", "appId3", "appId4")
      when(mockParityService.nextApplicationsForExport(any[Int])).thenReturn(Future.successful(
        appIds.map(ApplicationReadyForExport(_))
      ))

      when(mockParityService.exportApplication(eqTo(appIds(1)))(any[HeaderCarrier](), any[RequestHeader]())).thenReturn(Future.failed(
        new ConnectorException("Second application could not send")
      ))

      when(mockParityService.exportApplication(eqTo(appIds(3)))(any[HeaderCarrier](), any[RequestHeader]())).thenReturn(Future.failed(
        new ConnectorException("Second application could not send")
      ))

      when(mockParityService.exportApplication(any[String]())(any[HeaderCarrier](), any[RequestHeader]())).thenReturn(Future.successful(
        ()
      ))

      val result = scheduler.tryExecute().futureValue

      verify(mockParityService, times(1)).exportApplication(eqTo(appIds.head))(any[HeaderCarrier](), any[RequestHeader]())
      verify(mockParityService, times(1)).exportApplication(eqTo(appIds(1)))(any[HeaderCarrier](), any[RequestHeader]())
      verify(mockParityService, times(1)).exportApplication(eqTo(appIds(2)))(any[HeaderCarrier](), any[RequestHeader]())
      verify(mockParityService, times(1)).exportApplication(eqTo(appIds(3)))(any[HeaderCarrier](), any[RequestHeader]())
    }

    "do not export an application if none of them are ready for export" in new TestFixture {
      when(mockParityService.nextApplicationsForExport(any[Int])).thenReturn(Future.successful(
        Nil
      ))
      scheduler.tryExecute().futureValue

      verify(mockParityService, never).exportApplication(any())(any[HeaderCarrier](), any[RequestHeader]())
    }
  }

  trait TestFixture {
    val batchSize = 1
    val mockParityService = mock[ParityExportService]
    val mockParityJobConfig = ScheduledJobConfig(
      enabled = false,
      Some("test"),
      initialDelaySecs = None,
      intervalSecs = None,
      batchSize = Some(batchSize)
    )

    lazy val scheduler = new ParityExportJob {
      val service = mockParityService
      val parityExportJobConfig = mockParityJobConfig
      val config = ParityExportJobConfig
    }
  }
}
