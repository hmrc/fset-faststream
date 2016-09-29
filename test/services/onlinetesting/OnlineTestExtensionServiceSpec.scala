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

package services.onlinetesting

import factories.DateTimeFactory
import model.OnlineTestCommands.Phase1TestProfile
import model.ProgressStatuses.{ PHASE1_TESTS_EXPIRED, PHASE1_TESTS_FIRST_REMINDER, PHASE1_TESTS_SECOND_REMINDER, PHASE1_TESTS_STARTED }
import model.command.ProgressResponse
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import org.scalatestplus.play.PlaySpec
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import services.AuditService
import services.onlinetesting.OnlineTestService.TestExtensionException
import testkit.MockitoImplicits.{ OngoingStubbingExtension, OngoingStubbingExtensionUnit }
import testkit.MockitoSugar

import scala.concurrent.Future

class OnlineTestExtensionServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  "extendTestGroupExpiryTime" should {
    "return a successful Future" when {
      "add extra days onto expiry, from today's date, if expired" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getPhase1TestGroup(applicationId)).thenReturnAsync(successfulTestProfile)
        when(mockProgressResponse.phase1TestsExpired).thenReturn(true)
        when(mockDateFactory.nowLocalTimeZone).thenReturn(Now)
        when(mockProfile.expirationDate).thenReturn(OneHourAgo)
        when(mockOtRepository.updateGroupExpiryTime(eqTo(applicationId), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturnAsync()

        underTest.extendTestGroupExpiryTime(applicationId, extraDays).futureValue(timeout(Span(1, Seconds))) mustBe (())

        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockOtRepository).getPhase1TestGroup(eqTo(applicationId))
        verify(mockDateFactory).nowLocalTimeZone
        verify(mockOtRepository).updateGroupExpiryTime(eqTo(applicationId), eqTo(Now.plusDays(extraDays)))
        verify(mockAppRepository).removeProgressStatuses(eqTo(applicationId), eqTo(statusToRemoveWhenExpired))
        verify(mockAuditService).logEventNoRequest(eqTo("ExpiredTestsExtended"), eqTo(Map("applicationId" -> applicationId)))
        verifyNoMoreInteractions(mockAppRepository, mockOtRepository, mockAuditService, mockDateFactory)
      }
      "add extra days onto expiry, from the expiry time, if not expired" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getPhase1TestGroup(applicationId)).thenReturnAsync(successfulTestProfile)
        when(mockProgressResponse.phase1TestsExpired).thenReturn(false)
        when(mockProgressResponse.phase1TestsStarted).thenReturn(true)
        when(mockProfile.expirationDate).thenReturn(InFiveHours)
        when(mockOtRepository.updateGroupExpiryTime(eqTo(applicationId), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturnAsync()

        underTest.extendTestGroupExpiryTime(applicationId, extraDays).futureValue(timeout(Span(1, Seconds))) mustBe (())

        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockOtRepository).getPhase1TestGroup(eqTo(applicationId))
        verify(mockOtRepository).updateGroupExpiryTime(eqTo(applicationId), eqTo(InFiveHours.plusDays(extraDays)))
        verify(mockAppRepository).removeProgressStatuses(eqTo(applicationId), eqTo(statusToRemoveWhenAlmostExpired))
        verify(mockAuditService).logEventNoRequest(eqTo("NonExpiredTestsExtended"), eqTo(Map("applicationId" -> applicationId)))
        verifyNoMoreInteractions(mockAppRepository, mockOtRepository, mockAuditService, mockDateFactory)
      }
      "remove first reminder and started status when not expired and second reminder has not been sent" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getPhase1TestGroup(applicationId)).thenReturnAsync(successfulTestProfile)
        when(mockProgressResponse.phase1TestsExpired).thenReturn(false)
        when(mockProgressResponse.phase1TestsInvited).thenReturn(true)
        when(mockProfile.hasNotStartedYet).thenReturn(true)
        when(mockProfile.expirationDate).thenReturn(InTwentyFiveHours)
        when(mockOtRepository.updateGroupExpiryTime(eqTo(applicationId), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturnAsync()

        underTest.extendTestGroupExpiryTime(applicationId, extraDays).futureValue(timeout(Span(1, Seconds))) mustBe (())

        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockOtRepository).getPhase1TestGroup(eqTo(applicationId))
        verify(mockOtRepository).updateGroupExpiryTime(eqTo(applicationId), eqTo(InTwentyFiveHours.plusDays(extraDays)))
        verify(mockAppRepository).removeProgressStatuses(eqTo(applicationId), eqTo(statusToRemoveWhenNotStartedAndFirstReminderSent))
        verify(mockAuditService).logEventNoRequest(eqTo("NonExpiredTestsExtended"), eqTo(Map("applicationId" -> applicationId)))
        verifyNoMoreInteractions(mockAppRepository, mockOtRepository, mockAuditService, mockDateFactory)
      }
      "remove no status when no reminder has been sent and the test is not started" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getPhase1TestGroup(applicationId)).thenReturnAsync(successfulTestProfile)
        when(mockProgressResponse.phase1TestsExpired).thenReturn(false)
        when(mockProgressResponse.phase1TestsInvited).thenReturn(true)
        when(mockProfile.hasNotStartedYet).thenReturn(false)
        when(mockProfile.expirationDate).thenReturn(InMoreThanThreeDays)
        when(mockOtRepository.updateGroupExpiryTime(eqTo(applicationId), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturnAsync()

        underTest.extendTestGroupExpiryTime(applicationId, extraDays).futureValue(timeout(Span(1, Seconds))) mustBe (())

        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockOtRepository).getPhase1TestGroup(eqTo(applicationId))
        verify(mockOtRepository).updateGroupExpiryTime(eqTo(applicationId), eqTo(InMoreThanThreeDays.plusDays(extraDays)))
        verify(mockAppRepository, times(0)).removeProgressStatuses(eqTo(applicationId), any())
        verify(mockAuditService).logEventNoRequest(eqTo("NonExpiredTestsExtended"), eqTo(Map("applicationId" -> applicationId)))
        verifyNoMoreInteractions(mockAppRepository, mockOtRepository, mockAuditService, mockDateFactory)
      }
    }
    "return a failed Future" when {
      "the application status doesn't allow an extension" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getPhase1TestGroup(applicationId)).thenReturnAsync(successfulTestProfile)
        whenReady(underTest.extendTestGroupExpiryTime(applicationId, extraDays).failed) { e =>
          e mustBe (invalidStatusError)
        }

        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockOtRepository).getPhase1TestGroup(eqTo(applicationId))
        verifyNoMoreInteractions(mockAppRepository, mockOtRepository, mockAuditService, mockDateFactory)
      }
      "find progress fails" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturn(Future.failed(genericError))
        whenReady(underTest.extendTestGroupExpiryTime(applicationId, extraDays).failed) { e =>
          e mustBe (genericError)
        }
        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verifyNoMoreInteractions(mockAppRepository, mockOtRepository, mockAuditService, mockDateFactory)
      }
      "No test phase 1 profile is available" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getPhase1TestGroup(applicationId)).thenReturnAsync(None)
        whenReady(underTest.extendTestGroupExpiryTime(applicationId, extraDays).failed) { e =>
          e mustBe (noTestProfileFoundError)
        }
        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockOtRepository).getPhase1TestGroup(eqTo(applicationId))
        verifyNoMoreInteractions(mockAppRepository, mockOtRepository, mockAuditService, mockDateFactory)
      }
      "remove status return an error and no audit event is emitted" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getPhase1TestGroup(applicationId)).thenReturnAsync(successfulTestProfile)
        when(mockProgressResponse.phase1TestsExpired).thenReturn(true)
        when(mockDateFactory.nowLocalTimeZone).thenReturn(Now)
        when(mockProfile.expirationDate).thenReturn(OneHourAgo)
        when(mockOtRepository.updateGroupExpiryTime(eqTo(applicationId), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturn(Future.failed(genericError))

        whenReady(underTest.extendTestGroupExpiryTime(applicationId, extraDays).failed) { e =>
          e mustBe (genericError)
        }

        verify(mockAppRepository).removeProgressStatuses(eqTo(applicationId), eqTo(statusToRemoveWhenExpired))
        verifyZeroInteractions(mockAuditService)
      }

    }

  }

  trait TestFixture {
    val applicationId = "abc"
    val extraDays = 3
    val statusToRemoveWhenExpired = List(PHASE1_TESTS_EXPIRED, PHASE1_TESTS_FIRST_REMINDER, PHASE1_TESTS_SECOND_REMINDER)
    val statusToRemoveWhenAlmostExpired = List(PHASE1_TESTS_SECOND_REMINDER, PHASE1_TESTS_FIRST_REMINDER)
    val statusToRemoveWhenNotStartedAndFirstReminderSent = List(PHASE1_TESTS_STARTED, PHASE1_TESTS_FIRST_REMINDER)
    val invalidStatusError = new TestExtensionException("Application is in an invalid status for test extension")
    val noTestProfileFoundError = TestExtensionException("No Phase1TestGroupAvailable for the given application")
    val genericError = new Exception("Dummy error!")
    val mockDateFactory = mock[DateTimeFactory]
    val Now = DateTime.now()
    val OneHourAgo = Now.minusHours(1)
    val InFiveHours = Now.plusHours(5)
    val InTwentyFiveHours = Now.plusHours(25)
    val InMoreThanThreeDays = Now.plusHours(73)
    val mockProfile = mock[Phase1TestProfile]
    val mockProgressResponse = mock[ProgressResponse]
    val successfulProgressResponse = mockProgressResponse
    val successfulTestProfile = Some(mockProfile)
    val mockAppRepository = mock[GeneralApplicationRepository]
    val mockOtRepository = mock[OnlineTestRepository]
    val mockAuditService = mock[AuditService]
    val underTest = new OnlineTestExtensionServiceImpl(mockAppRepository, mockOtRepository, mockAuditService, mockDateFactory)
  }
}
