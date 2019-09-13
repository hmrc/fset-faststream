/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.LocalDateTime

import config.{ CSRHttp, SecurityEnvironmentImpl }
import connectors._
import connectors.exchange.referencedata.SchemeId
import connectors.ApplicationClient.{ CandidateAlreadyHasAnAnalysisExerciseException, OnlineTestNotFound }
import connectors.exchange.sift.SiftAnswersStatus
import connectors.exchange._
import models.ApplicationRoute._
import models.SecurityUserExamples._
import models._
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.Files
import testkit.MockitoImplicits._
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{ AnyContent, MultipartFormData, Request }
import play.api.test.Helpers._
import security.{ SilhouetteComponent, UserCacheService }
import testkit.{ BaseControllerSpec, TestableSecureActions }
import testkit.MockitoImplicits._

import scala.concurrent.Future
import java.io.File
import java.nio.file.Path
import java.util.UUID

import connectors.ReferenceDataExamples.Schemes
import connectors.exchange.candidateevents.CandidateAllocationWithEvent
import models.ApplicationData.ApplicationStatus
import models.CachedDataExample.SubmittedApplication
import models.events.AllocationStatuses
import org.joda.time.DateTime
import play.api.test.{ FakeHeaders, FakeRequest }
import uk.gov.hmrc.http.HeaderCarrier

class HomeControllerSpec extends BaseControllerSpec {

  // The current candidate needed by other methods than withdraw may be different, in that case, we might need
  // to split to tests of this file.
  // This is the implicit user
  override def currentCandidateWithApp: CachedDataWithApp = {
    CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.SubmittedApplication.copy(userId = ActiveCandidate.user.userID))
  }

  "present" should {
    "display the expected test result urls in the post online tests page" in new TestFixture {
      val sift = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.SiftApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(List(
        ReferenceDataExamples.Schemes.Dip
      ))
      when(mockApplicationClient.getPhase3Results(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockApplicationClient.getSiftResults(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockSiftClient.getSiftAnswersStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(None)
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockUserService.refreshCachedUser(eqTo(ActiveCandidate.user.userID))(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(ActiveCandidate))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturnAsync(None)

      mockPostOnlineTestsDashboardCalls()

      mockPhaseOneTwoThreeData(List(phase1Test1), List(phase2Test1))

      val result = controller(sift, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      checkAllResultsTitlesAndLinks(content)
    }

    "display the expected test result urls in the online test progress page when candidate has failed P1 tests" in new TestFixture {
      val candidateState = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.Phase1TestsFailedApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(List(
        ReferenceDataExamples.Schemes.Dip
      ))
      when(mockApplicationClient.getPhase3Results(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockApplicationClient.getSiftResults(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockSiftClient.getSiftAnswersStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(None)
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockUserService.refreshCachedUser(eqTo(ActiveCandidate.user.userID))(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(ActiveCandidate))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturnAsync(None)

      mockPostOnlineTestsDashboardCalls()

      mockPhaseOneTwoThreeData(List(phase1Test1))

      val result = controller(candidateState, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      checkPhase1ResultsLinks(content)
      content must not include phase2Test1ResultsReportLink
      content must not include phase3ResultsReportLink
    }

    "display the expected test result urls in the online test progress page when candidate has passed P1 tests" in new TestFixture {
      val candidateState = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.Phase1TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(List(
        ReferenceDataExamples.Schemes.Dip
      ))
      when(mockApplicationClient.getPhase3Results(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockApplicationClient.getSiftResults(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockSiftClient.getSiftAnswersStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(None)
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockUserService.refreshCachedUser(eqTo(ActiveCandidate.user.userID))(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(ActiveCandidate))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturnAsync(None)

      mockPostOnlineTestsDashboardCalls()

      mockPhaseOneTwoThreeData(List(phase1Test1))

      val result = controller(candidateState, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      checkPhase1ResultsLinks(content)
      content must not include phase2Test1ResultsReportLink
      content must not include phase3ResultsReportLink
    }

    "display the expected test result urls in the online test progress page when candidate has failed P2 tests" in new TestFixture {
      val candidateState = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.Phase2TestsFailedApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(List(
        ReferenceDataExamples.Schemes.Dip
      ))
      when(mockApplicationClient.getPhase3Results(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockApplicationClient.getSiftResults(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockSiftClient.getSiftAnswersStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(None)
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockUserService.refreshCachedUser(eqTo(ActiveCandidate.user.userID))(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(ActiveCandidate))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturnAsync(None)

      mockPostOnlineTestsDashboardCalls()

      mockPhaseOneTwoThreeData(List(phase1Test1), List(phase2Test1))

      val result = controller(candidateState, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      checkPhase1ResultsLinks(content)
      checkPhase2ResultsLinks(content)
      content must not include phase3ResultsReportLink
    }

    "display the expected test result urls in the online test progress page when candidate has passed P2 tests" in new TestFixture {
      val candidateState = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.Phase2TestsFailedApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(List(
        ReferenceDataExamples.Schemes.Dip
      ))
      when(mockApplicationClient.getPhase3Results(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockApplicationClient.getSiftResults(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockSiftClient.getSiftAnswersStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(None)
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockUserService.refreshCachedUser(eqTo(ActiveCandidate.user.userID))(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(ActiveCandidate))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturnAsync(None)

      mockPostOnlineTestsDashboardCalls()

      mockPhaseOneTwoThreeData(List(phase1Test1), List(phase2Test1))

      val result = controller(candidateState, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      checkPhase1ResultsLinks(content)
      checkPhase2ResultsLinks(content)
      content must not include phase3ResultsReportLink
    }

    "display the expected test result urls in the online test progress page when candidate has failed P3 tests" in new TestFixture {
      val phase3TestsFailedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE3_TESTS_FAILED,
        progress = ProgressExamples.Phase3TestsFailedCumulative)

      val candidateState = CachedDataWithApp(ActiveCandidate.user,
        phase3TestsFailedApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(List(
        ReferenceDataExamples.Schemes.Dip
      ))
      when(mockApplicationClient.getPhase3Results(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockApplicationClient.getSiftResults(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockSiftClient.getSiftAnswersStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(None)
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockUserService.refreshCachedUser(eqTo(ActiveCandidate.user.userID))(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(ActiveCandidate))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturnAsync(None)

      mockPostOnlineTestsDashboardCalls()

      mockPhaseOneTwoThreeData(List(phase1Test1), List(phase2Test1))

      val result = controller(candidateState, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      checkPhase1ResultsLinks(content)
      checkPhase2ResultsLinks(content)
      checkPhase3ResultsLink(content)
    }

    "display the expected test result urls in the online test progress page when candidate has passed P3 tests" in new TestFixture {
      val phase3TestsFailedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE3_TESTS_PASSED,
        progress = ProgressExamples.Phase3TestsPassedCumulative)

      val candidateState = CachedDataWithApp(ActiveCandidate.user,
        phase3TestsFailedApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(List(
        ReferenceDataExamples.Schemes.Dip
      ))
      when(mockApplicationClient.getPhase3Results(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockApplicationClient.getSiftResults(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockSiftClient.getSiftAnswersStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(None)
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockUserService.refreshCachedUser(eqTo(ActiveCandidate.user.userID))(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(ActiveCandidate))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturnAsync(None)

      mockPostOnlineTestsDashboardCalls()

      mockPhaseOneTwoThreeData(List(phase1Test1), List(phase2Test1))

      val result = controller(candidateState, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      checkPhase1ResultsLinks(content)
      checkPhase2ResultsLinks(content)
      checkPhase3ResultsLink(content)
    }

    "display home page" in new TestFixture {
      val previewApp = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.InProgressInPreviewApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockApplicationClient.getPhase1TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))
      val result = controller(previewApp).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content mustNot include("Fast Stream applications are now closed")
      content must include("""<ol class="step-by-step-coloured " id="sixSteps">""")
    }

    "display home page with submit disabled" in new TestFixture {
      val applicationRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None }
      val previewApp = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.InProgressInPreviewApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockApplicationClient.getPhase1TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))
      val result = controller(previewApp, applicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("Applications are now closed")
      content must include("""<ol class="step-by-step-coloured disabled" id="sixSteps">""")
    }

    "display post online tests page" in new TestFixture {
      val sift = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.SiftApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(List(
        ReferenceDataExamples.Schemes.Dip
      ))
      when(mockApplicationClient.getPhase3Results(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockApplicationClient.getSiftResults(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockSiftClient.getSiftAnswersStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
          .thenReturnAsync(None)
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockUserService.refreshCachedUser(eqTo(ActiveCandidate.user.userID))(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(ActiveCandidate))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturnAsync(None)

      mockPostOnlineTestsDashboardCalls()

      mockPhaseOneTwoThreeData()

      val result = controller(sift, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      content must include("Your current schemes are detailed below:")
      content mustNot include("Your application has been withdrawn.")
    }

    "display faststream final results page for withdrawn application" in new TestFixture {
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockApplicationClient.getPhase3Results(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockApplicationClient.getSiftResults(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockUserService.refreshCachedUser(eqTo(ActiveCandidate.user.userID))(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(ActiveCandidate))
      val withdrawnSiftApp = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.WithdrawnSiftApplication.copy(userId = ActiveCandidate.user.userID))
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(List(
        ReferenceDataExamples.Schemes.Generalist
      ))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier])).thenReturnAsync(None)
      when(mockSiftClient.getSiftAnswersStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
          .thenReturnAsync(None)

      mockPostOnlineTestsDashboardCalls()

      mockPhaseOneTwoThreeData()

      val result = controller(withdrawnSiftApp, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      content must include("Your application has been withdrawn.")
      content must include("Your current schemes are detailed below:")
    }

    "display edip final results page" in new EdipAndSdipTestFixture {
      mockPhaseOneTwoThreeData()
      val result = controller(edipPhase1TestsPassedApp, applicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      content must include("Congratulations, you're through to the next stage")
      content mustNot include("Your application has been withdrawn.")
    }

    "display sdip final results page" in new EdipAndSdipTestFixture {
      mockPhaseOneTwoThreeData()
      val result = controller(sdipPhase1TestsPassedApp, applicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      content must include("Congratulations, you're through to the next stage")
      content mustNot include("Your application has been withdrawn.")
    }

    "display edip final results page for withdrawn application" in new EdipAndSdipTestFixture {
      mockPhaseOneTwoThreeData()
      val result = controller(edipWithdrawnPhase1TestsPassedApp, applicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      content must include("Your application has been withdrawn.")
      content must include("Congratulations, you're through to the next stage")
    }

    "display sdip final results page for withdrawn application" in new EdipAndSdipTestFixture {
      mockPhaseOneTwoThreeData()
      val result = controller(sdipWithdrawnPhase1TestsPassedApp, applicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      content must include("Your application has been withdrawn.")
      content must include("Congratulations, you're through to the next stage")
    }

    "display fast pass rejected message" in new TestFixture {
      val fastPassRejectedInvitedToPhase1Application = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.fastPassRejectedInvitedToPhase1Application.copy(userId = ActiveCandidate.user.userID))

      when(mockApplicationClient.getPhase1TestProfile2(eqTo(fastPassRejectedInvitedToPhase1Application.application
        .applicationId)
      )(any[HeaderCarrier])).thenReturn(Future.failed(new OnlineTestNotFound))

      val result = controller(fastPassRejectedInvitedToPhase1Application, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      content must include("Unfortunately we've not been able to confirm that your Fast Pass is valid.")
    }

    "not display fast pass rejected message when phase1 tests are started" in new TestFixture {
      val fastPassRejectedPhase1StartedApplication = CachedDataWithApp(ActiveCandidate.user,
        CachedDataExample.fastPassRejectedPhase1StartedApplication.copy(userId = ActiveCandidate.user.userID))

      when(mockApplicationClient.getPhase1TestProfile2(eqTo(fastPassRejectedPhase1StartedApplication.application.applicationId)
      )(any[HeaderCarrier])).thenReturn(Future.failed(new OnlineTestNotFound))

      val result = controller(fastPassRejectedPhase1StartedApplication, commonApplicationRouteState).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)

      content mustNot include("Unfortunately we've not been able to confirm that your Fast Pass is valid.")
    }
  }

  "present with sdip eligibility info" should {
    "display eligibility information when faststream application is withdrawn" in new TestFixture {
      when(mockApplicationClient.getPhase1TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      val withdrawnApplication = currentCandidateWithApp.copy(application = CachedDataExample.WithdrawApplication)
      val result = controller(withdrawnApplication, commonApplicationRouteState).present(true)(fakeRequest)

      val content = contentAsString(result)

      content must include("Unfortunately, you withdrew your Civil Service Fast Stream application.")
    }

    "display eligibility information when faststream application is not submitted" in new TestFixture {
      when(mockApplicationClient.getPhase1TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      val inProgressApp = currentCandidateWithApp.copy(application = CachedDataExample.InProgressInAssistanceDetailsApplication)
      val result = controller(inProgressApp, commonApplicationRouteState).present(true)(fakeRequest)

      val content = contentAsString(result)

      content must include("submit your application for the Civil Service Fast Stream before the deadline")
    }

    "display eligibility information when faststream application is phase1 tests expired" in new TestFixture {
      when(mockApplicationClient.getPhase1TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      val phase1TestsExpiredCandidate = currentCandidateWithApp.copy(application = CachedDataExample.Phase1TestsExpiredApplication)
      val result = controller(phase1TestsExpiredCandidate, commonApplicationRouteState).present(true)(fakeRequest)

      val content = contentAsString(result)

      content must include("complete your online exercises for the Civil Service Fast Stream before the deadline")
    }

    "not display eligibility information when application route is not faststream" in new TestFixture {
      when(mockApplicationClient.getPhase1TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      val result = controller(currentCandidateWithEdipApp, commonApplicationRouteState).present(true)(fakeRequest)

      val content = contentAsString(result)

      content mustNot include("Continue as SDIP")
    }

    "not display eligibility information when faststream application is submitted" in new TestFixture {
      when(mockApplicationClient.getPhase1TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.failed(new OnlineTestNotFound))
      when(mockApplicationClient.findAdjustments(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      val submittedCandidate = currentCandidateWithApp.copy(application = CachedDataExample.SubmittedApplication)
      val result = controller(submittedCandidate, commonApplicationRouteState).present(true)(fakeRequest)

      val content = contentAsString(result)

      content mustNot include("Continue as SDIP")
    }
  }

  "submitAnalysisExercise" should {
    "show a too big message when file is too large" in new TestFixture {
      mockPostOnlineTestsDashboardCalls()
      fileUploadMocks(10000000)

      val result = controller().submitAnalysisExercise().apply(fakePostRequestWithContentMock)

      status(result) mustBe SEE_OTHER
      flash(result).get("danger") mustBe Some("Your analysis exercise must be less than 4MB")
    }

    "show success when preconditions for upload are met" in new TestFixture {
      mockPostOnlineTestsDashboardCalls()
      fileUploadMocks(3500000)

      val result = controller().submitAnalysisExercise().apply(fakePostRequestWithContentMock)

      status(result) mustBe SEE_OTHER
      flash(result).get("success") mustBe Some("You've successfully submitted your analysis exercise.")
    }

    "show an error if this candidate has already uploaded an analysis exercise" in new TestFixture {
      mockPostOnlineTestsDashboardCalls(hasAnalysisExerciseAlready = true)
      fileUploadMocks(3500000, analysisExerciseUploadedAlready = true)

      val result = controller().submitAnalysisExercise().apply(fakePostRequestWithContentMock)

      status(result) mustBe SEE_OTHER
      flash(result).get("danger") mustBe Some("There was a problem uploading your analysis exercise. You can try again or speak to an assessor.")
    }

    "show an error if the content type is not on the allowed list" in new TestFixture {
      mockPostOnlineTestsDashboardCalls()
      fileUploadMocks(3500000)

      val result = controller().submitAnalysisExercise().apply(fakePostRequestWithBadContentTypeMock)

      status(result) mustBe SEE_OTHER
      flash(result).get("danger") mustBe Some("Your analysis exercise must be in the .doc or .docx format")
    }

    "Show an error if the file POST is not as expected" in new TestFixture {
      mockPostOnlineTestsDashboardCalls()
      fileUploadMocks(3500000)

      val result = controller().submitAnalysisExercise().apply(fakePostRequestWithoutProperMultipartFormData)

      status(result) mustBe SEE_OTHER
      flash(result).get("danger") mustBe Some("There was a problem uploading your analysis exercise. You can try again or speak to an assessor.")
    }
  }

  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockRefDataClient = mock[ReferenceDataClient]
    val mockSiftClient = mock[SiftClient]
    val mockSchemeClient = mock[SchemeClient]
    val mockUserService = mock[UserCacheService]
    val mockSecurityEnvironment = mock[SecurityEnvironmentImpl]

    val anyContentMock = mock[AnyContent]

    val commonApplicationRouteState = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = true
      val applicationsStartDate = None
    }

    def multipartFormData(contentType: String, key: String = "analysisExerciseFile") = MultipartFormData[Files.TemporaryFile](
      dataParts = Map(),
      files = Seq(
        new FilePart[Files.TemporaryFile](key, "myFileName.docx", Some(contentType), TemporaryFile(fileMock))
      ),
      badParts = Seq()
    )

    def fakePostRequestWithoutProperMultipartFormData = FakeRequest("POST", "/", FakeHeaders(), anyContentMock).withMultipartFormDataBody(
      multipartFormData(msWordContentType, "randomWrongKey")
    )

    def fakePostRequestWithContentMock = FakeRequest("POST", "/", FakeHeaders(), anyContentMock).withMultipartFormDataBody(
      multipartFormData(msWordContentType)
    )

    def fakePostRequestWithBadContentTypeMock = FakeRequest("POST", "/", FakeHeaders(), anyContentMock).withMultipartFormDataBody(
      multipartFormData("application/octet-stream")
    )

    val fileMock = mock[File]
    val msWordContentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    def mockPostOnlineTestsDashboardCalls(hasAnalysisExerciseAlready: Boolean = false) = {

      val alloc = CandidateAllocationWithEvent("", "", AllocationStatuses.CONFIRMED, EventsExamples.Event1)

      when(mockApplicationClient.candidateAllocationEventWithSession(any[UniqueIdentifier])(any[HeaderCarrier]())).thenReturnAsync(List(alloc))

      when(mockApplicationClient.hasAnalysisExercise(any[UniqueIdentifier]())(any[HeaderCarrier])).thenReturnAsync(hasAnalysisExerciseAlready)
    }

    def fileUploadMocks(fileSize: Int, analysisExerciseUploadedAlready: Boolean = false) = {
      if (analysisExerciseUploadedAlready) {
        when(mockApplicationClient.uploadAnalysisExercise(any[UniqueIdentifier](),
          any[String](),
          any[Array[Byte]]())(any[HeaderCarrier])).thenReturn(Future.failed(new CandidateAlreadyHasAnAnalysisExerciseException))
      } else {
        when(mockApplicationClient.uploadAnalysisExercise(any[UniqueIdentifier](),
          any[String](),
          any[Array[Byte]]())(any[HeaderCarrier])).thenReturnAsync()
      }
      when(fileMock.length()).thenReturn(fileSize)
    }

    def mockPhaseOneTwoThreeData(phase1Tests: List[PsiTest] = Nil, phase2Tests: List[PsiTest] = Nil) = {

      when(mockApplicationClient.getPhase1TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Phase1TestGroupWithNames2(expirationDate = DateTime.now, activeTests = phase1Tests)))

      when(mockApplicationClient.getPhase2TestProfile2(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Phase2TestGroupWithActiveTest2(expirationDate = DateTime.now, activeTests = phase2Tests)))

      when(mockApplicationClient.getPhase3TestGroup(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Phase3TestGroup(expirationDate = DateTime.now, tests = Nil)))
    }

    // There are titles in the postOnlineTestsDashboard template
    def checkAllResultsTitlesAndLinks(content: String) = {
      checkPhase1ResultsTitleAndLinks(content)
      checkPhase2ResultsTitleAndLinks(content)
      checkPhase3ResultsTitleAndLinks(content)
    }

    val phase1Test1ResultsReportLink = "<a href=\"http://phase1Test1Url.com\"" +
      " target=\"_blank\" id=\"WorkStyleQuestionnairePart1LinkResultsReport\">Feedback report"

    def checkPhase1ResultsTitleAndLinks(content: String) = {
      content must include("Phase 1 results")
      checkPhase1ResultsLinks(content)
    }

    def checkPhase1ResultsLinks(content: String) = {
      content must include("<div>Work Style Questionnaire Part 1</div>")
      content must include(phase1Test1ResultsReportLink)
    }

    val phase2Test1ResultsReportLink = "<a href=\"http://phase2Test1Url.com\"" +
      " target=\"_blank\" id=\"CaseStudyAssessmentLinkResultsReport\">Feedback report"

    def checkPhase2ResultsTitleAndLinks(content: String) = {
      content must include("Phase 2 results")
      checkPhase2ResultsLinks(content)
    }

    def checkPhase2ResultsLinks(content: String) = {
      content must include("<div>Case Study Assessment</div>")
      content must include(phase2Test1ResultsReportLink)
    }

    val phase3ResultsReportLink = "<a href=\"/fset-fast-stream/online-tests/phase3/feedback-report\"" +
      " target=\"_blank\" id=\"phase3ResultsReportLink\" alt=\"Phase 3 feedback report\">"
    def checkPhase3ResultsTitleAndLinks(content: String) = {
      content must include("Phase 3 feedback")
      checkPhase3ResultsLink(content)
    }

    def checkPhase3ResultsLink(content: String) = {
      content must include(phase3ResultsReportLink)
    }

    val phase1Test1InventoryId = "45c7aee3-4d23-45c7-a09d-276df7db3e4c"
    val phase1Test1 = PsiTest(inventoryId = phase1Test1InventoryId, usedForResults = true,
      testUrl = "http://testurl.com", orderId = UniqueIdentifier(UUID.randomUUID()),
      invitationDate = DateTime.now, testResult = Some(PsiTestResult(testReportUrl = Some("http://phase1Test1Url.com"))))

    val phase2Test1InventoryId = "60b423e5-75d6-4d31-b02c-97b8686e22e6"
    val phase2Test1 = PsiTest(inventoryId = phase2Test1InventoryId, usedForResults = true,
      testUrl = "http://testurl.com", orderId = UniqueIdentifier(UUID.randomUUID()),
      invitationDate = DateTime.now, testResult = Some(PsiTestResult(testReportUrl = Some("http://phase2Test1Url.com"))))

    class TestableHomeController extends HomeController(mockApplicationClient, mockRefDataClient, mockSiftClient, mockSchemeClient)
      with TestableSecureActions {
      val http: CSRHttp = CSRHttp
      override val env = mockSecurityEnvironment
      override lazy val silhouette = SilhouetteComponent.silhouette
      val appRouteConfigMap = Map.empty[ApplicationRoute, ApplicationRouteState]
      val selectedSchemes = SelectedSchemes(Schemes.SomeSchemes.map(_.id.value), orderAgreed = true, eligible = true)

      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
      when(mockRefDataClient.allSchemes()(any[HeaderCarrier])).thenReturnAsync(ReferenceDataExamples.Schemes.AllSchemes)
      when(mockSchemeClient.getSchemePreferences(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(selectedSchemes)
      when(mockSiftClient.getSiftAnswersStatus(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(Some(SiftAnswersStatus.DRAFT))
      when(mockApplicationClient.getCurrentSchemeStatus(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(List(SchemeEvaluationResultWithFailureDetails(SchemeId("DiplomaticService"), SchemeStatus.Green)))
      when(mockUserService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(CachedData(ActiveCandidate.user, Some(CreatedApplication.copy(userId = ActiveCandidate.user.userID)))))

      when(mockApplicationClient.getAssistanceDetails(eqTo(currentUserId), eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))

      when(mockApplicationClient.getSiftState(eqTo(currentApplicationId))(any[HeaderCarrier]))
        .thenReturnAsync(None)

      mockPostOnlineTestsDashboardCalls()

      // Analysis file upload tests
      override protected def getAllBytesInFile(path: Path): Array[Byte] = {
        "This is a test string".toCharArray.map(_.toByte)
      }
    }

    def controller(implicit candWithApp: CachedDataWithApp = currentCandidateWithApp,
                   appRouteState: ApplicationRouteState = defaultApplicationRouteState) = new TestableHomeController {
      override val candidate: CachedData = CachedData(candWithApp.user, Some(candWithApp.application))
      override val candidateWithApp: CachedDataWithApp = candWithApp
      override val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> appRouteState, Sdip -> appRouteState)

      when(mockUserService.refreshCachedUser(any[UniqueIdentifier])(any[HeaderCarrier], any[Request[_]]))
        .thenReturn(Future.successful(candidate))
    }

    def defaultApplicationRouteState = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = true
      val applicationsStartDate = Some(LocalDateTime.now)
    }
  }

  trait EdipAndSdipTestFixture extends TestFixture {
    val applicationRouteState = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = true
      val applicationsStartDate = None }

    when(mockApplicationClient.getPhase3Results(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)
    when(mockApplicationClient.getSiftResults(any[UniqueIdentifier])(any[HeaderCarrier])).thenReturnAsync(None)

    when(mockApplicationClient.getPhase3Results(eqTo(currentApplicationId))(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(List(SchemeEvaluationResult(SchemeId("DiplomaticService"), SchemeStatus.Green)))))

    val edipPhase1TestsPassedApp = CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.EdipPhase1TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))
    val sdipPhase1TestsPassedApp = CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.SdipPhase1TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))
    val edipWithdrawnPhase1TestsPassedApp = CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.EdipWithdrawnPhase1TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))
    val sdipWithdrawnPhase1TestsPassedApp = CachedDataWithApp(ActiveCandidate.user,
      CachedDataExample.SdipWithdrawnPhase1TestsPassedApplication.copy(userId = ActiveCandidate.user.userID))
  }
}
