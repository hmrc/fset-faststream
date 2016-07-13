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
import mocks.ContactDetailsInMemoryRepository
import mocks.application.PersonalDetailsInMemoryRepository
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.ContactDetailsRepository
import repositories.application.PersonalDetailsRepository
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.language.postfixOps

class PersonalDetailsControllerSpec extends PlaySpec with Results {

  "Personal Details Controller" should {

    "create contact details and return the same details" in new TestFixture {
      val userId = "1234"
      val applicationId = "111-111"

      val request = s"""
                       |{
                       |  "firstName":"Clark",
                       |  "lastName":"Kent",
                       |  "preferredName":"Superman",
                       |  "email":"super@man.com",
                       |  "dateOfBirth":"2015-07-10",
                       |  "address": {
                       |      "line1":"North Pole 1",
                       |      "line2":"North Pole 2",
                       |      "line3":"North Pole 3",
                       |      "line4":"North Pole 4"
                       |   },
                       |  "postCode":"H0H 0H0",
                       |  "phone":"071234567",
                       |  "aLevel": true,
                       |  "stemLevel": false
                       |}
        """.stripMargin

      val result = TestPersonalDetailsController.personalDetails(userId, applicationId)(
        updatePersonalDetailsRequest(userId, applicationId)(request)
      )

      status(result) must be(201)

      verify(mockAuditService).logEvent(eqTo("PersonalDetailsSaved"))(any[HeaderCarrier], any[RequestHeader])

      val savedResult = TestPersonalDetailsController.find(userId, applicationId)(
        FakeRequest(Helpers.GET, "", FakeHeaders(), "")
      ).run

      contentAsJson(savedResult) must be(Json.parse(request))
    }

    "return a system error on invalid json" in new TestFixture {
      val result = TestPersonalDetailsController.personalDetails("1234", "111-111")(
        updatePersonalDetailsRequest("1234", "111-111")(
          s"""
           |{
           |  "wrongField1":"Clark",
           |  "wrongField2":"Kent",
           |  "preferredName":"Superman",
           |  "dateOfBirth":"2015-07-10",
           |  "address": {"line1":"North Pole"},
           |  "postCode": "H0H 0H0",
           |  "mobilePhone": "071234567"
           |}
        """.stripMargin
        )
      )

      status(result) must be(400)
    }
  }

  trait TestFixture extends TestFixtureBase {
    object TestPersonalDetailsController extends PersonalDetailsController {
      override val psRepository: PersonalDetailsRepository = PersonalDetailsInMemoryRepository
      override val cdRepository: ContactDetailsRepository = ContactDetailsInMemoryRepository
      override val auditService: AuditService = mockAuditService
    }

    def updatePersonalDetailsRequest(userId: String, applicationId: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.PersonalDetailsController.personalDetails(userId, applicationId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
