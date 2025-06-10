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

class UpscanCallbackControllerSpec extends UnitWithAppSpec {

  "callback" should {
    "store the callback data when the file scan was successful" in new TestFixture {
      val jsonString =
        s"""
           |{
           |  "reference":"47545ff3-336d-42ea-a9c6-0f0dcbf6f1d4",
           |  "downloadUrl":"http://localhost:9570/upscan/download/98d1b99e-b444-48fc-83c3-b1eaaeb635d0",
           |  "fileStatus":"READY",
           |  "uploadDetails":{
           |    "size":13209,
           |    "fileMimeType":"application/binary",
           |    "fileName":"Test document 1.docx",
           |    "checksum":"bf9b1b6e867ae6f1258069c0b6d5f81b0c819c52fff4b748b2523e6c15925d23",
           |    "uploadTimestamp":"2025-05-28T20:35:07.305058Z"
           |  }
           |}
           |""".stripMargin

      when(mockUpscanService.handleCallback(any[CallbackBody])).thenReturnAsync()

      val result = controller.callback()(createPostRequest(jsonString))
      status(result) mustBe OK

      val callback = ReadyCallbackBody(
        Reference("47545ff3-336d-42ea-a9c6-0f0dcbf6f1d4"),
        downloadUrl = java.net.URL("http", "localhost", 9570, "/upscan/download/98d1b99e-b444-48fc-83c3-b1eaaeb635d0"),
        UploadDetails(
          uploadTimestamp = java.time.Instant.parse("2025-05-28T20:35:07.305058Z"),
          checksum = "bf9b1b6e867ae6f1258069c0b6d5f81b0c819c52fff4b748b2523e6c15925d23",
          fileMimeType = "application/binary",
          fileName = "Test document 1.docx",
          size = 13209
        )
      )
      verify(mockUpscanService).handleCallback(eqTo(callback))
    }

    "store the callback data when the file scan failed" in new TestFixture {
      val jsonString =
        s"""
           |{
           |  "reference":"47545ff3-336d-42ea-a9c6-0f0dcbf6f1d4",
           |  "fileStatus":"FAILED",
           |  "failureDetails":{
           |    "failureReason":"Test failure reason",
           |    "message":"Test message"
           |  }
           |}
           |""".stripMargin

      when(mockUpscanService.handleCallback(any[CallbackBody])).thenReturnAsync()

      val result = controller.callback()(createPostRequest(jsonString))
      status(result) mustBe OK

      val callback = FailedCallbackBody(
        Reference("47545ff3-336d-42ea-a9c6-0f0dcbf6f1d4"),
        ErrorDetails(failureReason = "Test failure reason", message = "Test message")
      )
      verify(mockUpscanService).handleCallback(eqTo(callback))
    }
    
    "return BAD_REQUEST if the fileStatus is missing in the json" in new TestFixture {
      val jsonString =
        s"""
           |{
           |  "reference":"47545ff3-336d-42ea-a9c6-0f0dcbf6f1d4",
           |  "failureDetails":{
           |    "failureReason":"Test failure reason",
           |    "message":"Test message"
           |  }
           |}
           |""".stripMargin

      val result = controller.callback()(createPostRequest(jsonString))
      status(result) mustBe BAD_REQUEST
    }

    "return BAD_REQUEST if the fileStatus in the json contains an invalid value" in new TestFixture {
      val jsonString =
        s"""
           |{
           |  "reference":"47545ff3-336d-42ea-a9c6-0f0dcbf6f1d4",
           |  "fileStatus":"BOOM",
           |  "failureDetails":{
           |    "failureReason":"Test failure reason",
           |    "message":"Test message"
           |  }
           |}
           |""".stripMargin

      val result = controller.callback()(createPostRequest(jsonString))
      status(result) mustBe BAD_REQUEST
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockUpscanService = mock[UpscanService]

    val controller = new UpscanCallbackController(
      stubControllerComponents(playBodyParsers = stubPlayBodyParsers(materializer)),
      mockUpscanService
    )

    def createPostRequest(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.POST, controllers.upscan.routes.UpscanCallbackController.callback().url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
