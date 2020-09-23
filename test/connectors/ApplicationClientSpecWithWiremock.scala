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

package connectors

import java.util.UUID

import com.github.nscala_time.time.Imports.DateTime
import com.github.tomakehurst.wiremock.client.WireMock._
import config.{CSRHttp, FaststreamBackendConfig, FaststreamBackendUrl, FrontendAppConfig}
import connectors.ApplicationClient._
import connectors.UserManagementClient.TokenEmailPairInvalidException
import connectors.exchange.campaignmanagement.AfterDeadlineSignupCodeUnused
import connectors.exchange._
import models.{ApplicationRoute, ProgressResponseExamples, UniqueIdentifier}
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.UpstreamErrorResponse

/*
  These unit tests using wiremock are used to verify that the behaviour of the client/ connector is the same now
 with Play Upgrade 2.6 than it was before that.
  Play Upgrade 2.6 includes some updates in http-verb that imply a big change in how the HttpReads works, specially
 when we are using HttpReads[HttpResponse] which is the default behaviour if we do not parametrised the calls to the verbs
 (i.e. http.GET == http.GET[HttpResponse]).
  More information here: https://github.com/hmrc/http-verbs
  Otherwise it is easier and faster to run test without wiremock.
 */
class ApplicationClientSpecWithWiremock extends BaseConnectorWithWiremockSpec {
  "afterDeadlineSignupCodeUnusedAndValid" should {
    val code = "theCode"
    val endpoint = s"/faststream/campaign-management/afterDeadlineSignupCodeUnusedAndValid?code=$code"

    "return afterDeadlineSignupCodeUnusedAndValid value" in new TestFixture {
      val response = AfterDeadlineSignupCodeUnused(true, Some(DateTime.now()))
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString())
      ))

      val result = client.afterDeadlineSignupCodeUnusedAndValid(code).futureValue

      result mustBe response
    }

    "return UpstreamErrorResponse(500) when there is an internal server error" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(INTERNAL_SERVER_ERROR)
      ))

      val result = client.afterDeadlineSignupCodeUnusedAndValid(code).failed.futureValue

      result mustBe an[UpstreamErrorResponse]
      result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe 500
    }
  }

  "createApplication" should {
    val UserId1 = UniqueIdentifier(UUID.randomUUID())
    val FrameworkId1 = "faststream2020"
    val ApplicationId1 = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/faststream/application/create"
    val request = CreateApplicationRequest(UserId1, FrameworkId1, ApplicationRoute.Faststream)

    "return application response if OK" in new TestFixture {
      val response = ApplicationResponse(
        ApplicationId1, "status", ApplicationRoute.Faststream, UserId1, ProgressResponseExamples.Initial,
        None, None)
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString())
      ))

      val result = client.createApplication(UserId1, FrameworkId1, ApplicationRoute.Faststream).futureValue

      result mustBe response
    }

    "return UpstreamErrorResponse(500) when there is an internal server error" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(INTERNAL_SERVER_ERROR)
      ))

      val result = client.createApplication(UserId1, FrameworkId1, ApplicationRoute.Faststream).failed.futureValue

      result mustBe an[UpstreamErrorResponse]
      result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe 500
    }
  }

  "overrideSubmissionDeadline" should {
    val ApplicationId1 = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/faststream/application/overrideSubmissionDeadline/$ApplicationId1"
    val request = OverrideSubmissionDeadlineRequest(DateTime.now())

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(OK)
      ))

      val result = client.overrideSubmissionDeadline(ApplicationId1, request).futureValue

      result mustBe unit
    }

    "throw CannotSubmitOverriddenSubmissionDeadline when 500 is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(INTERNAL_SERVER_ERROR)
      ))

      val result = client.overrideSubmissionDeadline(ApplicationId1, request).failed.futureValue

      result mustBe an[CannotSubmitOverriddenSubmissionDeadline]
    }
  }

  "markSignupCodeAsUsed" should {
    val ApplicationId1 = UniqueIdentifier(UUID.randomUUID())
    val code = "theCode"
    val endpoint = s"/faststream/application/markSignupCodeAsUsed?code=$code&applicationId=${ApplicationId1.toString}"
    "return unit when OK is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK)
      ))

      val result = client.markSignupCodeAsUsed(code, ApplicationId1).futureValue

      result mustBe unit
    }

    "throw CannotMarkSignupCodeAsUsed when 500 is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(INTERNAL_SERVER_ERROR)
      ))

      val result = client.markSignupCodeAsUsed(code, ApplicationId1).failed.futureValue

      result mustBe an[CannotMarkSignupCodeAsUsed]
    }
  }

  "withdrawApplication" should {
    val ApplicationId1 = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/faststream/application/withdraw/$ApplicationId1"
    val request = WithdrawApplication("reason", None)

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(OK)
      ))

      val result = client.withdrawApplication(ApplicationId1, request).futureValue

      result mustBe unit
    }

    "throw SiftExpired exception when FORBIDDEN is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(FORBIDDEN)
      ))

      val result = client.withdrawApplication(ApplicationId1, request).failed.futureValue

      result mustBe an[SiftExpired]
    }

    "throw CannotWithdraw exception when NOT_FOUND is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.withdrawApplication(ApplicationId1, request).failed.futureValue

      result mustBe an[CannotWithdraw]
    }

    "throw UpstreamErrorResponse when INTERNAL_SERVER_ERROR is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(INTERNAL_SERVER_ERROR)
      ))

      val result = client.withdrawApplication(ApplicationId1, request).failed.futureValue

      result mustBe an[UpstreamErrorResponse]
      result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
    }
  }

  "verifyInvigilatedToken" should {
    val endpoint = s"/faststream/online-test/phase2/verifyAccessCode"
    val Email = "joe@blogs.com"
    val Token = "token"
    val request = VerifyInvigilatedTokenUrlRequest(Email.toLowerCase, Token)

    "return InvigilatedTestUrl when OK is received" in new TestFixture {
      val response = InvigilatedTestUrl("url")
      stubFor(post(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString())
      ))

      val result = client.verifyInvigilatedToken(Email, Token).futureValue

      result mustBe response
    }

    "throw TokenEmailPairInvalidException exception when NOT_FOUND is received" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.verifyInvigilatedToken(Email, Token).failed.futureValue

      result mustBe an[TokenEmailPairInvalidException]
    }

    "throw TestForTokenExpiredException exception when FORBIDDEN is received" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(FORBIDDEN)
      ))

      val result = client.verifyInvigilatedToken(Email, Token).failed.futureValue

      result mustBe an[TestForTokenExpiredException]
    }

    "throw UpstreamErrorResponse when INTERNAL_SERVER_ERROR is received" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(INTERNAL_SERVER_ERROR)
      ))

      val result = client.verifyInvigilatedToken(Email, Token).failed.futureValue

      result mustBe an[UpstreamErrorResponse]
      result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
    }
  }

  trait TestFixture extends BaseConnectorTestFixture {
    val mockConfig = new FrontendAppConfig(mockConfiguration, mockEnvironment) {
      val faststreamUrl = FaststreamBackendUrl(s"http://localhost:$wireMockPort", "/faststream")
      override lazy val faststreamBackendConfig = FaststreamBackendConfig(faststreamUrl)
    }
    val ws = app.injector.instanceOf(classOf[WSClient])
    val http = new CSRHttp(ws, app)
    val client = new ApplicationClient(mockConfig, http)
  }
}
