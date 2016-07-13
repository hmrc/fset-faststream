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

package controllers

import config.TestFixtureBase
import mocks.application.PersonalDetailsInMemoryRepository
import mocks.{ FrameworkInMemoryRepository, FrameworkPreferenceInMemoryRepository }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc.{ RequestHeader, Results }
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.PersonalDetailsRepository
import repositories.{ FrameworkPreferenceRepository, FrameworkRepository }
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

// Note: Primary purpose is to unit test auditing. More comprehensive
// tests are in the `it` (integration test) folder.
class FrameworkPreferenceControllerSpec extends PlaySpec with Results {

  "Framework preference controller" should {

    "submit the first preference" in new TestFixture {
      val result = TestFrameworkPreferenceController.submitFirstPreference("111-111")(submitFirstPreferenceRequest("111-111")(
        s"""
           |{
           |  "region":"Winter",
           |  "location": "Wonder",
           |  "firstFramework":"Land"
           |}
        """.stripMargin
      ))

      status(result) must be(200)
      verify(mockAuditService).logEvent(eqTo("FirstLocationSaved"))(any[HeaderCarrier], any[RequestHeader])
    }

    "submit the second preference" in new TestFixture {
      val result = TestFrameworkPreferenceController.submitSecondPreference("111-111")(submitSecondPreferenceRequest("111-111")(
        s"""
           |{
           |  "region":"Winter",
           |  "location": "Wonderful",
           |  "firstFramework":"Land"
           |}
        """.stripMargin
      ))

      status(result) must be(200)
      verify(mockAuditService).logEvent(eqTo("SecondLocationSaved"))(any[HeaderCarrier], any[RequestHeader])
    }

    "submit 'yes' as second preference intention" in new TestFixture {
      val result = TestFrameworkPreferenceController.
        submitSecondPreferenceIntention("111-111")(submitSecondPreferenceIntentionRequest("111-111")(
          s"""
           |{
           |  "secondPreferenceIntended":true
           |}
        """.stripMargin
        ))

      status(result) must be(200)
      verify(mockAuditService).logEvent(eqTo("SecondLocationIntended"))(any[HeaderCarrier], any[RequestHeader])
    }

    "submit 'no' as second preference intention" in new TestFixture {
      val result = TestFrameworkPreferenceController
        .submitSecondPreferenceIntention("111-111")(submitSecondPreferenceIntentionRequest("111-111")(
          s"""
           |{
           |  "secondPreferenceIntended":false
           |}
        """.stripMargin
        ))

      status(result) must be(200)
      verify(mockAuditService).logEvent(eqTo("SecondLocationNotIntended"))(any[HeaderCarrier], any[RequestHeader])
    }

    "submit the alternatives" in new TestFixture {
      val result = TestFrameworkPreferenceController.submitAlternatives("111-111")(submitAlternativesRequest("111-111")(
        s"""
           |{
           |  "location": true,
           |  "framework": false
           |}
        """.stripMargin
      ))

      status(result) must be(200)
      verify(mockAuditService).logEvent(eqTo("AlternativesSubmitted"))(any[HeaderCarrier], any[RequestHeader])
    }
  }

  trait TestFixture extends TestFixtureBase {
    object TestFrameworkPreferenceController extends FrameworkPreferenceController {
      override val frameworkRepository: FrameworkRepository = FrameworkInMemoryRepository
      override val frameworkPreferenceRepository: FrameworkPreferenceRepository = FrameworkPreferenceInMemoryRepository
      override val auditService: AuditService = mockAuditService
      override val personalDetailsRepository: PersonalDetailsRepository = PersonalDetailsInMemoryRepository
    }

    def submitFirstPreferenceRequest(applicationId: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.FrameworkPreferenceController.submitFirstPreference(applicationId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    def submitSecondPreferenceRequest(applicationId: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.FrameworkPreferenceController.submitSecondPreference(applicationId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    def submitSecondPreferenceIntentionRequest(applicationId: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.FrameworkPreferenceController.submitSecondPreferenceIntention(applicationId).url,
        FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    def submitAlternativesRequest(applicationId: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.FrameworkPreferenceController.submitAlternatives(applicationId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
