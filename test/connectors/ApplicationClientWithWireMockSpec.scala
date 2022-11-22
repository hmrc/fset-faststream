/*
 * Copyright 2022 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import config.{CSRHttp, FaststreamBackendConfig, FaststreamBackendUrl, FrontendAppConfig}
import connectors.ApplicationClient._
import connectors.UserManagementClient.TokenEmailPairInvalidException
import connectors.events.{Event, Location, Session, Venue}
import connectors.exchange._
import connectors.exchange.campaignmanagement.AfterDeadlineSignupCodeUnused
import connectors.exchange.candidateevents.{CandidateAllocation, CandidateAllocationWithEvent, CandidateAllocations}
import connectors.exchange.candidatescores.CompetencyAverageResult
import connectors.exchange.referencedata.SchemeId
import connectors.exchange.sift.SiftState
import mappings.Address
import models.events.{AllocationStatuses, EventType}
import models.{Adjustments, ApplicationRoute, ProgressResponseExamples, UniqueIdentifier}
import org.joda.time.{DateTime, LocalDate, LocalTime}
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.util.UUID

/*
  These unit tests using wiremock are used to verify that the behaviour of the client/connector is the same now
  with Play Upgrade 2.6 as it was before that.
  Play Upgrade 2.6 includes some updates in http-verb that imply a big change in how the HttpReads works, specially
  when we are using HttpReads[HttpResponse] which is the default behaviour if we do not parameterize the calls to the verbs
  (i.e. http.GET == http.GET[HttpResponse]).
  More information here: https://github.com/hmrc/http-verbs
  Otherwise it is easier and faster to run test without wiremock.
 */
class ApplicationClientWithWireMockSpec extends BaseConnectorWithWireMockSpec {

  val base = "candidate-application"

  "afterDeadlineSignupCodeUnusedAndValid" should {
    val code = "theCode"
    val endpoint = s"/$base/campaign-management/afterDeadlineSignupCodeUnusedAndValid"

    "return afterDeadlineSignupCodeUnusedAndValid value" in new TestFixture {
      val response = AfterDeadlineSignupCodeUnused(unused = true, expires = Some(DateTime.now()))

      stubFor(get(urlPathEqualTo(endpoint))
        .withQueryParam("code", WireMock.equalTo(code))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.afterDeadlineSignupCodeUnusedAndValid(code).futureValue
      result mustBe response
    }

    "return UpstreamErrorResponse(500) when there is an internal server error" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).withQueryParam("code", WireMock.equalTo(code)).willReturn(
        aResponse().withStatus(INTERNAL_SERVER_ERROR)
      ))

      val result = client.afterDeadlineSignupCodeUnusedAndValid(code).failed.futureValue
      result mustBe an[UpstreamErrorResponse]
      result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
    }
  }

  "createApplication" should {
    val userId = UniqueIdentifier(UUID.randomUUID())
    val frameworkId = "faststream2020"
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/create"
    val request = CreateApplicationRequest(userId, frameworkId, ApplicationRoute.Faststream, sdipDiversity = None)

    "return application response if OK" in new TestFixture {
      val response = ApplicationResponse(
        applicationId, "status", ApplicationRoute.Faststream, userId, ProgressResponseExamples.Initial,
        civilServiceExperienceDetails = None, overriddenSubmissionDeadline = None)
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString())
      ))

      val result = client.createApplication(userId, frameworkId, sdipDiversity = None, ApplicationRoute.Faststream).futureValue
      result mustBe response
    }

    "return UpstreamErrorResponse(500) when there is an internal server error" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.createApplication(userId, frameworkId, sdipDiversity = None, ApplicationRoute.Faststream).failed.futureValue
      result mustBe an[UpstreamErrorResponse]
      result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
    }
  }

  "overrideSubmissionDeadline" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/overrideSubmissionDeadline/$applicationId"
    val request = OverrideSubmissionDeadlineRequest(DateTime.now())

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.overrideSubmissionDeadline(applicationId, request).futureValue
      result mustBe unit
    }

    "throw CannotSubmitOverriddenSubmissionDeadline when 500 is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(INTERNAL_SERVER_ERROR)
      ))

      val result = client.overrideSubmissionDeadline(applicationId, request).failed.futureValue
      result mustBe a[CannotSubmitOverriddenSubmissionDeadline]
    }
  }

  "markSignupCodeAsUsed" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val code = "theCode"
    val endpoint = s"/$base/application/markSignupCodeAsUsed"

    "return unit when OK is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint))
        .withQueryParam("code", WireMock.equalTo(code))
        .withQueryParam("applicationId", WireMock.equalTo(applicationId.toString))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.markSignupCodeAsUsed(code, applicationId).futureValue
      result mustBe unit
    }

    "throw CannotMarkSignupCodeAsUsed when 500 is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(INTERNAL_SERVER_ERROR)
      ))

      val result = client.markSignupCodeAsUsed(code, applicationId).failed.futureValue
      result mustBe a[CannotMarkSignupCodeAsUsed]
    }
  }

  "submitApplication" should {
    val userId = UniqueIdentifier(UUID.randomUUID())
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/submit/$userId/$applicationId"

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson("").toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.submitApplication(userId, applicationId).futureValue
      result mustBe unit
    }

    "throw CannotSubmit exception when BAD_REQUEST is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson("").toString()))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val result = client.submitApplication(userId, applicationId).failed.futureValue
      result mustBe a[CannotSubmit]
    }

    "throw UpstreamErrorResponse when INTERNAL_SERVER_ERROR is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson("").toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.submitApplication(userId, applicationId).failed.futureValue
      result mustBe a[UpstreamErrorResponse]
      result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
    }
  }

  "withdrawApplication" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/withdraw/$applicationId"
    val request = WithdrawApplication("reason", None)

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.withdrawApplication(applicationId, request).futureValue
      result mustBe unit
    }

    "throw CannotWithdraw exception when NOT_FOUND is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString())).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.withdrawApplication(applicationId, request).failed.futureValue
      result mustBe a[CannotWithdraw]
    }

    "throw UpstreamErrorResponse when INTERNAL_SERVER_ERROR is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.withdrawApplication(applicationId, request).failed.futureValue
      result mustBe an[UpstreamErrorResponse]
      result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
    }
  }

  "withdrawScheme" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/$applicationId/scheme/withdraw"
    val request = WithdrawScheme(SchemeId("Generalist"), reason = "My reason")

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.withdrawScheme(applicationId, request).futureValue
      result mustBe unit
    }

    "throw SiftExpired exception when FORBIDDEN is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(FORBIDDEN))
      )

      val result = client.withdrawScheme(applicationId, request).failed.futureValue
      result mustBe a[SiftExpired]
    }

    "throw CannotWithdraw when INTERNAL_SERVER_ERROR is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.withdrawScheme(applicationId, request).failed.futureValue
      result mustBe an[CannotWithdraw]
    }
  }

  "addReferral" should {
    val userId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/media/create"
    val referral = "My referral"
    val request = AddReferral(userId, referral)

    "return unit when CREATED is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(CREATED))
      )

      val result = client.addReferral(userId, referral).futureValue
      result mustBe unit
    }

    "throw CannotAddReferral exception when BAD_REQUEST is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val result = client.addReferral(userId, referral).failed.futureValue
      result mustBe a[CannotAddReferral]
    }
  }

  "getApplicationProgress" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/progress/$applicationId"
    val response = ProgressResponse(applicationId.toString)

    "return ProgressResponse when OK is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.getApplicationProgress(applicationId).futureValue
      result mustBe response
    }
  }

  "findApplication" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val userId = UniqueIdentifier(UUID.randomUUID())
    val frameworkId = "FastStream-2016"
    val endpoint = s"/$base/application/find/user/$userId/framework/$frameworkId"
    val response = ApplicationResponse(
      applicationId: UniqueIdentifier,
      applicationStatus = "SUBMITTED",
      applicationRoute = ApplicationRoute.Faststream,
      userId,
      progressResponse = ProgressResponse(applicationId.toString),
      civilServiceExperienceDetails = None,
      overriddenSubmissionDeadline = None
    )

    "return ApplicationResponse when OK is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.findApplication(userId, frameworkId).futureValue
      result mustBe response
    }

    "throw ApplicationNotFound exception when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(NOT_FOUND)
        ))

      val result = client.findApplication(userId, frameworkId).failed.futureValue
      result mustBe a[ApplicationNotFound]
    }
  }

  "findFsacEvaluationAverages" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/$applicationId/fsacEvaluationAverages"
    val response = CompetencyAverageResult(
      makingEffectiveDecisionsAverage = 4.0,
      workingTogetherDevelopingSelfAndOthersAverage = 4.0,
      communicatingAndInfluencingAverage = 4.0,
      seeingTheBigPictureAverage = 4.0,
      overallScore = 16.0
    )

    "return CompetencyAverageResult when OK is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.findFsacEvaluationAverages(applicationId).futureValue
      result mustBe response
    }

    "throw FsacEvaluatedAverageScoresNotFound exception when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(NOT_FOUND)
        ))

      val result = client.findFsacEvaluationAverages(applicationId).failed.futureValue
      result mustBe a[FsacEvaluatedAverageScoresNotFound]
    }
  }

  "updatePersonalDetails" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val userId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/personal-details/$userId/$applicationId"

    val personalDetails = GeneralDetails(
      firstName = "Joe",
      lastName = "Bloggs",
      preferredName = "Joe",
      email = "joebloggs@test.com",
      dateOfBirth = LocalDate.now(),
      outsideUk = false,
      address = Address(line1 = "Line 1", line2 = None, line3 = None, line4 = None),
      postCode = None,
      fsacIndicator = None,
      country = None,
      phone = None,
      civilServiceExperienceDetails = None,
      edipCompleted = None,
      edipYear = None,
      otherInternshipCompleted = None,
      otherInternshipName = None,
      otherInternshipYear = None,
      updateApplicationStatus = None
    )

    "return unit when CREATED is received" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(personalDetails).toString()))
        .willReturn(aResponse().withStatus(CREATED))
      )

      val result = client.updatePersonalDetails(applicationId, userId, personalDetails).futureValue
      result mustBe unit
    }

    "throw CannotUpdateRecord exception when BAD_REQUEST is received" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(personalDetails).toString()))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val result = client.updatePersonalDetails(applicationId, userId, personalDetails).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }
  }

  "getPersonalDetails" should {
    val userId = UniqueIdentifier(UUID.randomUUID())
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/personal-details/$userId/$applicationId"

    val personalDetails = GeneralDetails(
      firstName = "Joe",
      lastName = "Bloggs",
      preferredName = "Joe",
      email = "joebloggs@test.com",
      dateOfBirth = LocalDate.now(),
      outsideUk = false,
      address = Address(line1 = "Line 1", line2 = None, line3 = None, line4 = None),
      postCode = None,
      fsacIndicator = None,
      country = None,
      phone = None,
      civilServiceExperienceDetails = None,
      edipCompleted = None,
      edipYear = None,
      otherInternshipCompleted = None,
      otherInternshipName = None,
      otherInternshipYear = None,
      updateApplicationStatus = None
    )

    "return GeneralDetails when CREATED is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(personalDetails).toString)
      ))

      val result = client.getPersonalDetails(userId, applicationId).futureValue
      result mustBe personalDetails
    }

    "throw PersonalDetailsNotFound exception when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(aResponse().withStatus(NOT_FOUND)))

      val result = client.getPersonalDetails(userId, applicationId).failed.futureValue
      result mustBe a[PersonalDetailsNotFound]
    }
  }

  "updateAssistanceDetails" should {
    val userId = UniqueIdentifier(UUID.randomUUID())
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/assistance-details/$userId/$applicationId"

    val assistanceDetails = AssistanceDetails(
      hasDisability = "No",
      disabilityImpact = None,
      disabilityCategories = None,
      otherDisabilityDescription = None,
      guaranteedInterview = None,
      needsSupportForOnlineAssessment = None,
      needsSupportForOnlineAssessmentDescription = None,
      needsSupportAtVenue = None,
      needsSupportAtVenueDescription = None,
      needsSupportForPhoneInterview = None,
      needsSupportForPhoneInterviewDescription = None
    )

    "return unit when CREATED is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(assistanceDetails).toString()))
        .willReturn(aResponse().withStatus(CREATED))
      )

      val result = client.updateAssistanceDetails(applicationId, userId, assistanceDetails).futureValue
      result mustBe unit
    }

    "return CannotUpdateRecord when BAD_REQUEST is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(assistanceDetails).toString()))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val result = client.updateAssistanceDetails(applicationId, userId, assistanceDetails).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }
  }

  "getAssistanceDetails" should {
    val userId = UniqueIdentifier(UUID.randomUUID())
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/assistance-details/$userId/$applicationId"

    val assistanceDetails = AssistanceDetails(
      hasDisability = "No",
      disabilityImpact = None,
      disabilityCategories = None,
      otherDisabilityDescription = None,
      guaranteedInterview = None,
      needsSupportForOnlineAssessment = None,
      needsSupportForOnlineAssessmentDescription = None,
      needsSupportAtVenue = None,
      needsSupportAtVenueDescription = None,
      needsSupportForPhoneInterview = None,
      needsSupportForPhoneInterviewDescription = None
    )

    "return AssistanceDetails when OK is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(assistanceDetails).toString)
      ))

      val result = client.getAssistanceDetails(userId, applicationId).futureValue
      result mustBe assistanceDetails
    }

    "return AssistanceDetailsNotFound when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.getAssistanceDetails(userId, applicationId).failed.futureValue
      result mustBe a[AssistanceDetailsNotFound]
    }
  }

  "updateQuestionnaire" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val sectionId = "sectionId"
    val endpoint = s"/$base/questionnaire/$applicationId/$sectionId"

    val questionnaire = Questionnaire(
      List(Question(question = "question1", Answer(answer = Some("Answer"), otherDetails = None, unknown = None)))
    )

    "return unit when ACCEPTED is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(questionnaire).toString()))
        .willReturn(aResponse().withStatus(ACCEPTED))
      )

      val result = client.updateQuestionnaire(applicationId, sectionId, questionnaire).futureValue
      result mustBe unit
    }

    "return CannotUpdateRecord when BAD_REQUEST is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(questionnaire).toString()))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val result = client.updateQuestionnaire(applicationId, sectionId, questionnaire).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }
  }

  "updatePreview" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/preview/$applicationId"

    val previewRequest = PreviewRequest(flag = true)

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(previewRequest).toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.updatePreview(applicationId).futureValue
      result mustBe unit
    }

    "return CannotUpdateRecord when BAD_REQUEST is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(previewRequest).toString()))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      val result = client.updatePreview(applicationId).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }
  }

  "verifyInvigilatedToken" should {
    val endpoint = s"/$base/online-test/phase2/verifyAccessCode"
    val email = "joe@blogs.com"
    val token = "token"
    val request = VerifyInvigilatedTokenUrlRequest(email.toLowerCase, token)

    "return InvigilatedTestUrl when OK is received" in new TestFixture {
      val response = InvigilatedTestUrl("url")
      stubFor(post(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString()))
      )

      val result = client.verifyInvigilatedToken(email, token).futureValue
      result mustBe response
    }

    "throw TokenEmailPairInvalidException exception when NOT_FOUND is received" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.verifyInvigilatedToken(email, token).failed.futureValue
      result mustBe a[TokenEmailPairInvalidException]
    }

    "throw TestForTokenExpiredException exception when FORBIDDEN is received" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(FORBIDDEN))
      )

      val result = client.verifyInvigilatedToken(email, token).failed.futureValue
      result mustBe a[TestForTokenExpiredException]
    }

    "throw UpstreamErrorResponse when INTERNAL_SERVER_ERROR is received" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(request).toString()))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      val result = client.verifyInvigilatedToken(email, token).failed.futureValue

      result mustBe an[UpstreamErrorResponse]
      result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
    }
  }

  "getPhase1Tests" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/phase1-tests/$applicationId"

    "return psiTests when OK is received" in new TestFixture {
      val response = Seq(PsiTest(
        inventoryId = "inventoryId",
        usedForResults = true,
        testUrl = "test@test.com",
        orderId = UniqueIdentifier(UUID.randomUUID()),
        invitationDate = DateTime.now
      ))

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getPhase1Tests(applicationId).futureValue
      result mustBe response
    }

    "throw OnlineTestNotFound when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.getPhase1Tests(applicationId).failed.futureValue
      result mustBe an[OnlineTestNotFound]
    }
  }

  "getPhase1TestProfile2" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/online-test/psi/phase1/candidate/$applicationId"

    "return Phase1TestGroupWithNames2 when OK is received" in new TestFixture {
      val activeTests = Seq(PsiTest(
        inventoryId = "inventoryId",
        usedForResults = true,
        testUrl = "test@test.com",
        orderId = UniqueIdentifier(UUID.randomUUID()),
        invitationDate = DateTime.now
      ))
      val response = Phase1TestGroupWithNames(expirationDate = DateTime.now, activeTests)

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getPhase1TestProfile(applicationId).futureValue
      result mustBe response
    }

    "throw OnlineTestNotFound when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.getPhase1TestProfile(applicationId).failed.futureValue
      result mustBe an[OnlineTestNotFound]
    }
  }

  "getPhase2TestProfile2" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/online-test/phase2/candidate/$applicationId"

    "return Phase2TestGroupWithActiveTest2 when OK is received" in new TestFixture {
      val activeTests = Seq(PsiTest(
        inventoryId = "inventoryId",
        usedForResults = true,
        testUrl = "test@test.com",
        orderId = UniqueIdentifier(UUID.randomUUID()),
        invitationDate = DateTime.now
      ))
      val response = Phase2TestGroupWithActiveTest(expirationDate = DateTime.now, activeTests)

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getPhase2TestProfile(applicationId).futureValue
      result mustBe response
    }

    "throw OnlineTestNotFound when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.getPhase2TestProfile(applicationId).failed.futureValue
      result mustBe an[OnlineTestNotFound]
    }
  }

  "getPhase1TestGroupWithNames2ByOrderId" should {
    val orderId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/online-test/phase1/candidate/orderId/$orderId"

    "return Phase1TestGroupWithNames2 when OK is received" in new TestFixture {
      val activeTests = Seq(PsiTest(
        inventoryId = "inventoryId",
        usedForResults = true,
        testUrl = "test@test.com",
        orderId = UniqueIdentifier(UUID.randomUUID()),
        invitationDate = DateTime.now
      ))
      val response = Phase1TestGroupWithNames(expirationDate = DateTime.now, activeTests)

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getPhase1TestGroupWithNamesByOrderId(orderId).futureValue
      result mustBe response
    }

    "throw OnlineTestNotFound when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.getPhase1TestGroupWithNamesByOrderId(orderId).failed.futureValue
      result mustBe an[OnlineTestNotFound]
    }
  }

  "getPhase2TestProfile2ByOrderId" should {
    val orderId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/online-test/phase2/candidate/orderId/$orderId"

    "return Phase2TestGroupWithNames2 when OK is received" in new TestFixture {
      val activeTests = Seq(PsiTest(
        inventoryId = "inventoryId",
        usedForResults = true,
        testUrl = "test@test.com",
        orderId = UniqueIdentifier(UUID.randomUUID()),
        invitationDate = DateTime.now
      ))
      val response = Phase2TestGroupWithActiveTest(expirationDate = DateTime.now, activeTests)

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getPhase2TestProfileByOrderId(orderId).futureValue
      result mustBe response
    }

    "throw OnlineTestNotFound when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.getPhase2TestProfileByOrderId(orderId).failed.futureValue
      result mustBe an[OnlineTestNotFound]
    }
  }

  "completeTestByOrderId" should {
    val orderId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/psi/$orderId/complete"

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson("").toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.completeTestByOrderId(orderId).futureValue
      result mustBe unit
    }
  }

  "getPhase3TestGroup" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/phase3-test-group/$applicationId"

    "return Phase3TestGroup when OK is received" in new TestFixture {
      val activeTests = List(Phase3Test(
        usedForResults = true,
        testUrl = "test@test.com",
        token = "testtoken",
        invitationDate = DateTime.now,
        callbacks = LaunchpadTestCallbacks(reviewed = Nil)
      ))

      val response = Phase3TestGroup(expirationDate = DateTime.now, activeTests, evaluation = None)

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getPhase3TestGroup(applicationId).futureValue
      result mustBe response
    }

    "throw OnlineTestNotFound when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.getPhase3TestGroup(applicationId).failed.futureValue
      result mustBe an[OnlineTestNotFound]
    }
  }

  "getPhase3Results" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/$applicationId/phase3/results"

    "return phase3 results when OK is received" in new TestFixture {
      val response = Some(List(SchemeEvaluationResult(SchemeId("Generalist"), result = "Green")))

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getPhase3Results(applicationId).futureValue
      result mustBe response
    }

    "return no results when NOT_FOUND is received, indicating no data found" in new TestFixture {
      val response = None

      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.getPhase3Results(applicationId).futureValue
      result mustBe response
    }
  }

  "getSiftTestGroup2" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/psi/sift-test-group/$applicationId"

    "return SiftTestGroupWithActiveTest when OK is received" in new TestFixture {
      val activeTest = PsiTest(
        inventoryId = "inventoryId",
        usedForResults = true,
        testUrl = "test@test.com",
        orderId = UniqueIdentifier(UUID.randomUUID()),
        invitationDate = DateTime.now
      )
      val response = SiftTestGroupWithActiveTest(expirationDate = DateTime.now, activeTest)

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getSiftTestGroup(applicationId).futureValue
      result mustBe response
    }

    "throw SiftTestNotFound when NOT_FOUND is received" in new TestFixture {
      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(NOT_FOUND)
      ))

      val result = client.getSiftTestGroup(applicationId).failed.futureValue
      result mustBe a[SiftTestNotFound]
    }
  }

  "getSiftState" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/sift-candidate/state/$applicationId"

    "return SiftState when OK is received" in new TestFixture {
      val response = Some(SiftState(siftEnteredDate = DateTime.now, expirationDate = DateTime.now))

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getSiftState(applicationId).futureValue
      result mustBe response
    }

    "return no results when NOT_FOUND is received, indicating no data found" in new TestFixture {
      val response = None

      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.getSiftState(applicationId).futureValue
      result mustBe response
    }
  }

  "getSiftResults" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/$applicationId/sift/results"

    "return scheme evaluation results when OK is received" in new TestFixture {
      val response = Some(List(SchemeEvaluationResult(SchemeId("Generalist"), result = "Green")))

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getSiftResults(applicationId).futureValue
      result mustBe response
    }

    "return no results when NOT_FOUND is received, indicating no data found" in new TestFixture {
      val response = None

      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.getSiftResults(applicationId).futureValue
      result mustBe response
    }
  }

  "getCurrentSchemeStatus" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/$applicationId/currentSchemeStatus"

    "return scheme evaluation results when OK is received" in new TestFixture {
      val response = Seq(SchemeEvaluationResultWithFailureDetails(SchemeId("Generalist"), result = "Green", failedAt = Some("PHASE1")))

      stubFor(get(urlPathEqualTo(endpoint)).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.getCurrentSchemeStatus(applicationId).futureValue
      result mustBe response
    }
  }

  "startPhase3TestByToken" should {
    val launchpadInviteId = "inviteId"
    val endpoint = s"/$base/launchpad/$launchpadInviteId/markAsStarted"

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson("").toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.startPhase3TestByToken(launchpadInviteId).futureValue
      result mustBe unit
    }
  }

  "completePhase3TestByToken" should {
    val launchpadInviteId = "inviteId"
    val endpoint = s"/$base/launchpad/$launchpadInviteId/markAsComplete"

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson("").toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.completePhase3TestByToken(launchpadInviteId).futureValue
      result mustBe unit
    }
  }

  "startTest" should {
    val orderId = "orderId"
    val endpoint = s"/$base/psi/$orderId/start"

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson("").toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.startTest(orderId).futureValue
      result mustBe unit
    }
  }

  "startSiftTest" should {
    val orderId = "orderId"
    val endpoint = s"/$base/psi/sift-test/$orderId/start"

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson("").toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.startSiftTest(orderId).futureValue
      result mustBe unit
    }
  }

  "confirmAllocation" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/allocation-status/confirm/$applicationId"

    "return unit when OK is received" in new TestFixture {
      stubFor(post(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson("").toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.confirmAllocation(applicationId).futureValue
      result mustBe unit
    }
  }

  "candidateAllocationEventWithSession" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/candidate-allocations/sessions/findByApplicationId"

    "return a list of CandidateAllocationWithEvent objects when OK is received" in new TestFixture {
      val event = Event(
        applicationId,
        EventType.FSAC,
        "Description",
        Location("Location"),
        Venue(name = "Venue name", description = "Venue description"),
        date = LocalDate.now,
        capacity = 10,
        minViableAttendees = 8,
        attendeeSafetyMargin = 2,
        startTime = LocalTime.now,
        endTime = LocalTime.now,
        skillRequirements = Map("Skill" -> 1),
        sessions = List(
          Session(
            applicationId,
            "Description",
            capacity = 10,
            minViableAttendees = 8,
            attendeeSafetyMargin = 2,
            startTime = LocalTime.now,
            endTime = LocalTime.now
          )
        )
      )

      val response = List(CandidateAllocationWithEvent(applicationId.toString, version = "v1", AllocationStatuses.CONFIRMED, event))

      stubFor(get(urlPathEqualTo(endpoint))
        .withQueryParam("applicationId", WireMock.equalTo(applicationId.toString))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.candidateAllocationEventWithSession(applicationId).futureValue
      result mustBe response
    }
  }

  "confirmCandidateAllocation" should {
    val eventId = UniqueIdentifier(UUID.randomUUID())
    val sessionId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/candidate-allocations/confirm-allocation/events/$eventId/sessions/$sessionId"

    val candidateAllocations = CandidateAllocations(
      version = Some("v1"),
      List(CandidateAllocation("id", AllocationStatuses.CONFIRMED, removeReason = None))
    )

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(candidateAllocations).toString()))
        .willReturn(aResponse().withStatus(OK))
      )

      val result = client.confirmCandidateAllocation(eventId, sessionId, candidateAllocations).futureValue
      result mustBe unit
    }

    "throw an OptimisticLockException when CONFLICT is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint))
        .withRequestBody(equalTo(Json.toJson(candidateAllocations).toString()))
        .willReturn(aResponse().withStatus(CONFLICT))
      )

      val result = client.confirmCandidateAllocation(eventId, sessionId, candidateAllocations).failed.futureValue
      result mustBe an[OptimisticLockException]
    }
  }

  "hasAnalysisExercise" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/hasAnalysisExercise"

    "return a value indicating if the candidate has analysis exercise when OK is received" in new TestFixture {
      val response = true
      stubFor(get(urlPathEqualTo(endpoint))
        .withQueryParam("applicationId", WireMock.equalTo(applicationId.toString))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
        ))

      val result = client.hasAnalysisExercise(applicationId).futureValue
      result mustBe response
    }
  }

  "findAdjustments" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/adjustments/$applicationId"

    "return adjustments when OK is received" in new TestFixture {
      val response = Adjustments(adjustments = Some(List("Adjustment1")), adjustmentsConfirmed = Some(true))

      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(OK).withBody(Json.toJson(response).toString)
      ))

      val result = client.findAdjustments(applicationId).futureValue
      result mustBe Some(response)
    }

    "return no adjustments when NOT_FOUND is received, indicating no data found" in new TestFixture {
      val response = None

      stubFor(get(urlPathEqualTo(endpoint))
        .willReturn(aResponse().withStatus(NOT_FOUND))
      )

      val result = client.findAdjustments(applicationId).futureValue
      result mustBe response
    }
  }

  "considerForSdip" should {
    val applicationId = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/consider-for-sdip/$applicationId"

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson("").toString())).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson("").toString())
      ))

      val result = client.considerForSdip(applicationId).futureValue
      result mustBe unit
    }
  }

  "continueAsSdip" should {
    val userId = UniqueIdentifier(UUID.randomUUID())
    val userIdToArchiveWith = UniqueIdentifier(UUID.randomUUID())
    val endpoint = s"/$base/application/continue-as-sdip/$userId/$userIdToArchiveWith"

    "return unit when OK is received" in new TestFixture {
      stubFor(put(urlPathEqualTo(endpoint)).withRequestBody(equalTo(Json.toJson("").toString())).willReturn(
        aResponse().withStatus(OK).withBody(Json.toJson("").toString())
      ))

      val result = client.continueAsSdip(userId, userIdToArchiveWith).futureValue
      result mustBe unit
    }
  }

  trait TestFixture extends BaseConnectorTestFixture {
    val mockConfig = new FrontendAppConfig(mockConfiguration, mockEnvironment) {
      val faststreamUrl = FaststreamBackendUrl(s"http://localhost:$wireMockPort", s"/$base")
      override lazy val faststreamBackendConfig = FaststreamBackendConfig(faststreamUrl)
    }
    val ws = app.injector.instanceOf(classOf[WSClient])
    val http = new CSRHttp(ws, app)
    val client = new ApplicationClient(mockConfig, http)
  }
}
