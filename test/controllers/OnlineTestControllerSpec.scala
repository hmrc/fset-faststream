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

/* TODO FAST STREAM FIX ME!!! Refactor to use mocks

package controllers

import config._
import connectors.ExchangeObjects._
import connectors.{ OnlineTestsGatewayClient, EmailClient }
import factories.{ DateTimeFactory, UUIDFactory }
import mocks._
import mocks.application.{ DocumentRootInMemoryRepository, OnlineTestInMemoryRepository }
import model.Address
import model.OnlineTestCommands.OnlineTestApplication
import model.PersistedObjects.ContactDetails
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.ContactDetailsRepository
import services.onlinetesting.{ OnlineTestExtensionService, OnlineTestService }
import testkit.MockitoImplicits.OngoingStubbingExtensionUnit
import testkit.MockitoSugar
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class OnlineTestControllerSpec extends UnitSpec with Results with MockitoSugar {
  "Get Online Test" should {

    "get an online test" in new TestFixture {
      val userId = ""
      val result = TestOnlineTestController.getOnlineTest(userId)(createOnlineTestRequest(userId)).run
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "onlineTestLink").as[String] must be("http://www.google.co.uk")
    }
  }

  "Update Online Test Status" should {

    "update an online test status" in new TestFixture {
      val result = TestOnlineTestController.onlineTestStatusUpdate("1234")(createOnlineTestStatusRequest(
        "1234",
        s"""
           |{
           |  "status":"Started"
           |}
        """.stripMargin
      ))

      status(result) must be(200)
    }
  }

  "Asking for a userId with a token" should {

    "return the userId if the token is valid" in new TestFixture {
      val token = "1234"
      val result = TestOnlineTestController.completeTests(token)(createOnlineTestCompleteRequest(token)).run

      status(result) must be(200)
    }
  }

  "Reset online tests" should {

    "fail if application not found" in new TestFixture {
      val appId = ""
      val result = TestOnlineTestController.resetOnlineTests(appId)(createResetOnlineTestRequest(appId)).run

      status(result) must be(NOT_FOUND)
    }

    "successfully reset the online test status" in new TestFixture {
      val appId = "appId"

      val result = TestOnlineTestController2.resetOnlineTests(appId)(createResetOnlineTestRequest(appId)).run

      status(result) must be(OK)
    }

  }

  "Extend online tests" should {

    "fail if application not found" in new TestFixture {
      val appId = ""
      val extraDays = 5
      val result = TestOnlineTestController.extendOnlineTests(appId)(createExtendOnlineTests(appId, extraDays))

      status(result) must be(NOT_FOUND)
    }

    "successfully reset the online test status" in new TestFixture {
      val appId = ""
      val extraDays = 5
      val result = TestOnlineTestController2.extendOnlineTests(appId)(createExtendOnlineTests(appId, extraDays))

      status(result) must be(OK)
    }

  }


  trait TestFixture extends TestFixtureBase {

    implicit val hc = HeaderCarrier()

    val hasPDFReportApplicationId = "has-pdf-report-application-id"
    val hasNoPDFReportApplicationId = "has-no-pdf-report-application-id"
    val testPDFContents = Array[Byte](0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20)

    val onlineTestExtensionServiceMock = mock[OnlineTestExtensionService]
    when(onlineTestExtensionServiceMock.extendExpiryTime(any(), any())).thenReturnAsync()

    val onlineTestsGatewayClientMock = mock[OnlineTestsGatewayClient]
    val emailClientMock = mock[EmailClient]

    when(emailClientMock.sendOnlineTestInvitation(any(), any(), any())(any())).thenReturn(
      Future.successful(())
    )

    when(onlineTestsGatewayClientMock.registerApplicant(any())(any())).thenReturn(Future.successful(Registration(0)))
    when(onlineTestsGatewayClientMock.inviteApplicant(any())(any())).thenReturn(Future.successful(Invitation(0, "", "", "", "", 0)))

    class OnlineTestServiceMock extends OnlineTestService {
      val appRepository = DocumentRootInMemoryRepository
      val cdRepository: ContactDetailsRepository = ContactDetailsInMemoryRepository
      val otRepository = OnlineTestInMemoryRepository
      val trRepository = TestReportInMemoryRepository
      val onlineTestsGatewayClient = onlineTestsGatewayClientMock
      val tokenFactory = UUIDFactory
      val onlineTestInvitationDateFactory = DateTimeFactory
      val emailClient = emailClientMock
      val auditService = mockAuditService
      val gatewayConfig = CubiksGatewayConfig(
        "",
        CubiksGatewaysScheduleIds(List(0, 0), List(0)),
        CubiksGatewayVerbalAndNumericalAssessment(1, 33, 1, 6, 12, 2, 6, 12),
        CubiksGatewayStandardAssessment(31, 32),
        CubiksGatewayStandardAssessment(41, 42),
        ReportConfig(1, 2, "en-GB"),
        "",
        ""
      )

      override def getOnlineTest(userId: String): Future[OnlineTest] = Future.successful {
        val date = DateTime.now
        OnlineTest(date, date.plusDays(4), "http://www.google.co.uk", "123@test.com", isOnlineTestEnabled = true, pdfReportAvailable = false)
      }
    }

    object OnlineTestServiceMock extends OnlineTestServiceMock

    object TestOnlineTestController extends OnlineTestController {
      override val onlineRepository = OnlineTestInMemoryRepository
      override val onlineTestingService = OnlineTestServiceMock
      override val onlineTestExtensionService = onlineTestExtensionServiceMock
    }

    object TestOnlineTestController2 extends OnlineTestController {
      override val onlineRepository = new OnlineTestInMemoryRepository {
        override def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = {
          Future.successful(
            Some(
              OnlineTestApplication(appId, "", "", false, false, "", None)
            )
          )
        }
      }
      override val onlineTestingService = new OnlineTestServiceMock {
        override val cdRepository = new ContactDetailsInMemoryRepository {
          override def find(applicationId: String): Future[ContactDetails] = {
            Future.successful(ContactDetails(Address(""), "", "", None))
          }
        }
      }
      override val onlineTestExtensionService = onlineTestExtensionServiceMock
    }

    def createOnlineTestRequest(userId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.OnlineTestController.getOnlineTest(userId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def createOnlineTestStatusRequest(userId: String, jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.POST, controllers.routes.OnlineTestController.onlineTestStatusUpdate(userId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    def createOnlineTestCompleteRequest(token: String) = {
      FakeRequest(Helpers.GET, controllers.routes.OnlineTestController.completeTests(token).url, FakeHeaders(), "")
    }

    def createResetOnlineTestRequest(appId: String) = {
      FakeRequest(Helpers.POST, controllers.routes.OnlineTestController.resetOnlineTests(appId).url, FakeHeaders(), "")
    }

    def createExtendOnlineTests(appId: String, extraDays: Int) = {
      val json = Json.parse(s"""{"extraDays":$extraDays}""")
      FakeRequest(Helpers.POST, controllers.routes.OnlineTestController.extendOnlineTests(appId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}*/
