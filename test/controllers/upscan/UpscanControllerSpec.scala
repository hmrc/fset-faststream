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

package controllers.upscan

import config.TestFixtureBase
import model.exchange.upscan.{Reference, UploadId, UploadStatusWithAppId, UploadStatuses}
import org.mockito.ArgumentMatchers.{eq as eqTo, *}
import org.mockito.Mockito.*
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.*
import play.api.test.Helpers.*
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import services.upscan.UpscanService
import testkit.MockitoImplicits.*
import testkit.UnitWithAppSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class UpscanControllerSpec extends UnitWithAppSpec {

  "requestUpload" should {
    "store the request upload data" in new TestFixture {
      val uploadId = UploadId("uploadId")
      val fileReference = Reference("fileReference")
      when(mockUpscanService.requestUpload(any[String], any[UploadId], any[Reference])).thenReturnAsync()
      val result = upscanController.requestUpload(uploadId, fileReference, AppId)(fakeRequest)
      status(result) mustBe OK
      // TODO: fix!!
//      verify(mockUpscanService).requestUpload(eqTo(AppId), eqTo(uploadId), eqTo(fileReference))
    }
  }

  "registerUploadResult" should {
    "store the new upload status" in new TestFixture {
      val jsonString =
        s"""
           |{
           |  "_type":"InProgress"
           |}
           |""".stripMargin

      val json = Json.parse(jsonString)
      val reference = Reference("fileReference")
      val request = FakeRequest(
        Helpers.PUT, controllers.upscan.routes.UpscanController.registerUploadResult(reference).url, FakeHeaders(), json
      ).withHeaders("Content-Type" -> "application/json")

//      when(mockUpscanService.registerUploadResult(eqTo(Reference("fileReference")), eqTo(UploadStatuses.InProgress))).thenReturnAsync()
//      when(mockUpscanService.registerUploadResult(eqTo(reference), eqTo(UploadStatuses.Failed))).thenReturnAsync()
      when(mockUpscanService.registerUploadResult(any[Reference], any[UploadStatuses.UploadStatus])).thenReturnAsync()

      val result = upscanController.registerUploadResult(reference)(request)
      status(result) mustBe OK
      // TODO: fix!!
//      verify(mockUpscanService).registerUploadResult(eqTo(reference2), eqTo(UploadStatuses.InProgress))
    }
  }

  "getUploadResult" should {
    "return data if found" in new TestFixture {
      val uploadStatusWithAppId = UploadStatusWithAppId("appId", UploadStatuses.InProgress)

      when(mockUpscanService.getUploadResult(any[UploadId])).thenReturnAsync(Some(uploadStatusWithAppId))

      val uploadId = UploadId("uploadId")
      val result = upscanController.getUploadResult(uploadId)(fakeRequest)
      status(result) mustBe OK
      asUploadStatusWithAppId(result) mustBe uploadStatusWithAppId
    }

    "return NOT_FOUND if no data found" in new TestFixture {
      when(mockUpscanService.getUploadResult(any[UploadId])).thenReturnAsync(None)

      val uploadId = UploadId("uploadId")
      val result = upscanController.getUploadResult(uploadId)(fakeRequest)
      status(result) mustBe NOT_FOUND
    }
  }

  "getUploadResultForFileReference" should {
    "return data if found" in new TestFixture {
      val uploadStatusWithAppId = UploadStatusWithAppId("appId", UploadStatuses.InProgress)

      when(mockUpscanService.getUploadResultForFileReference(any[Reference])).thenReturnAsync(Some(uploadStatusWithAppId))

      val fileReference = Reference("fileReference")
      val result = upscanController.getUploadResultForFileReference(fileReference)(fakeRequest)
      status(result) mustBe OK
      asUploadStatusWithAppId(result) mustBe uploadStatusWithAppId
    }

    "return NOT_FOUND if no data found" in new TestFixture {
      when(mockUpscanService.getUploadResultForFileReference(any[Reference])).thenReturnAsync(None)

      val fileReference = Reference("fileReference")
      val result = upscanController.getUploadResultForFileReference(fileReference)(fakeRequest)
      status(result) mustBe NOT_FOUND
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockUpscanService = mock[UpscanService]

    val upscanController = new UpscanController(
      stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
      mockUpscanService
    )

    def asUploadStatusWithAppId(response: Future[Result]): UploadStatusWithAppId =
      contentAsJson(response).as[JsValue].as[UploadStatusWithAppId]
  }
}
