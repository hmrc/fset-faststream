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
import mocks.application.AssistanceDetailsInMemoryRepository
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.AssistanceDetailsRepository
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.language.postfixOps

class AssistanceDetailsControllerSpec extends PlaySpec with Results {

  "Update assistance details" should {

    "update then details when we have only assistance and no adjustments" in new TestFixture {
      val result = TestAssistanceController.assistanceDetails("1234", "111-111")(updateAssistanceDetailsRequest("1234", "111-111")(
        s"""
           |{
           |  "needsAssistance":"Yes",
           |  "typeOfdisability": ["Some disability"],
           |  "needsAdjustment":"No"
           |}
        """.stripMargin
      ))

      status(result) must be(201)
      verify(mockAuditService).logEvent(eqTo("AssistanceDetailsSaved"))(any[HeaderCarrier], any[RequestHeader])
    }

    "update then details when we have only assistance and adjustments" in new TestFixture {
      val result = TestAssistanceController.assistanceDetails("1234", "111-111")(updateAssistanceDetailsRequest("1234", "111-111")(
        s"""
           |{
           |  "needsAssistance":"Yes",
           |  "typeOfdisability": ["Some disability"],
           |  "needsAdjustment":"Yes",
           |  "extraTime": true,
           |  "screenMagnification": true,
           |  "printCopies": true
           |}
        """.stripMargin
      ))

      status(result) must be(201)
      verify(mockAuditService).logEvent(eqTo("AssistanceDetailsSaved"))(any[HeaderCarrier], any[RequestHeader])
    }

    "return an error on invalid json" in new TestFixture {
      val result = TestAssistanceController.assistanceDetails("1234", "111-111")(updateAssistanceDetailsRequest("1234", "111-111")(
        s"""
           |{
           |  "wrongField1":"some value",
           |  "wrongField2":"other value"
           |}
        """.stripMargin
      ))

      status(result) must be(400)
    }
  }

  trait TestFixture extends TestFixtureBase {
    object TestAssistanceController extends AssistanceController {
      override val asRepository: AssistanceDetailsRepository = AssistanceDetailsInMemoryRepository
      override val auditService: AuditService = mockAuditService
    }

    def updateAssistanceDetailsRequest(userId: String, applicationId: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.AssistanceController.assistanceDetails(userId, applicationId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
