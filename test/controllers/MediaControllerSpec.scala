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

package controllers

import config.TestFixtureBase
import model.persisted.Media
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.MediaRepository
import services.AuditService
import testkit.MockitoImplicits._
import testkit.UnitWithAppSpec

import scala.language.postfixOps
import uk.gov.hmrc.http.HeaderCarrier

class MediaControllerSpec extends UnitWithAppSpec {

  "Add Media" should {

    "add media entry" in new TestFixture {
      val result = TestMediaController.addMedia()(createMedia(
        s"""
           |{
           |  "userId":"1234546789",
           |  "media":"Google"
           |}
        """.stripMargin
      ))

      status(result) mustBe CREATED
      verify(mockAuditService).logEvent(eqTo("CampaignReferrerSaved"))(any[HeaderCarrier], any[RequestHeader])
    }

    "return an error on invalid json" in new TestFixture {
      val result = TestMediaController.addMedia()(createMedia(
        s"""
           |{
           |  "wrongField1":"12345678",
           |  "wrongField2":"what reason"
           |}
        """.stripMargin
      ))

      status(result) mustBe BAD_REQUEST
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockMediaRepo = mock[MediaRepository]

    object TestMediaController extends MediaController {
      override val mRepository: MediaRepository = mockMediaRepo
      override val auditService: AuditService = mockAuditService

      when(mockMediaRepo.create(any[Media])).thenReturnAsync()
    }

    def createMedia(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.MediaController.addMedia().url, FakeHeaders(),
        json).withHeaders("Content-Type" -> "application/json")
    }
  }
}
