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
import model.persisted.Phase1TestProfile
import model.ProgressStatuses.{ PHASE1_TESTS_EXPIRED, PHASE1_TESTS_FIRST_REMINDER, PHASE1_TESTS_SECOND_REMINDER, PHASE1_TESTS_STARTED }
import model.command.{ Phase1ProgressResponse, ProgressResponse }
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.mvc.RequestHeader
import repositories.onlinetesting.Phase1TestRepository
import repositories.application.GeneralApplicationRepository
import services.AuditService
import services.events.EventServiceFixture
import services.onlinetesting.Exceptions.TestExtensionException
import testkit.MockitoImplicits.{ OngoingStubbingExtension, OngoingStubbingExtensionUnit }
import testkit.{ MockitoSugar, ShortTimeout }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class OnlineTestExtensionServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar with ShortTimeout {

  "extendTestGroupExpiryTime" should {
    "return a successful Future" when {
      "add extra days onto expiry, from today's date, if expired" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getTestGroup(applicationId)).thenReturnAsync(successfulTestProfile)
        when(mockProgressResponse.phase1ProgressResponse.phase1TestsExpired).thenReturn(true)
        when(mockDateFactory.nowLocalTimeZone).thenReturn(Now)
        when(mockProfile.expirationDate).thenReturn(OneHourAgo)
        when(mockOtRepository.updateGroupExpiryTime(eqTo(applicationId), any(), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturnAsync()

        val result = underTest.extendTestGroupExpiryTime(applicationId, twoExtraDays, "triggeredBy").futureValue

        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockOtRepository).getTestGroup(eqTo(applicationId))
        verify(mockDateFactory).nowLocalTimeZone
        verify(mockOtRepository).updateGroupExpiryTime(eqTo(applicationId), eqTo(Now.plusDays(twoExtraDays)), any())
        verify(mockAppRepository).removeProgressStatuses(eqTo(applicationId), eqTo(statusToRemoveWhenExpiryInMoreThanOneDayExpired))
      }
      "add extra days onto expiry, from the expiry time, if not expired" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getTestGroup(applicationId)).thenReturnAsync(successfulTestProfile)
        when(mockProgressResponse.phase1ProgressResponse.phase1TestsExpired).thenReturn(false)
        when(mockProgressResponse.phase1ProgressResponse.phase1TestsStarted).thenReturn(true)
        when(mockProfile.expirationDate).thenReturn(InFiveHours)
        when(mockOtRepository.updateGroupExpiryTime(eqTo(applicationId), any(), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturnAsync()

        underTest.extendTestGroupExpiryTime(applicationId, threeExtraDays, "triggeredBy").futureValue
        underTest.verifyAuditEvents(1, "NonExpiredTestsExtended")
        underTest.verifyDataStoreEvents(1, "OnlineExerciseExtended")

        verify(mockOtRepository).updateGroupExpiryTime(eqTo(applicationId), eqTo(InFiveHours.plusDays(threeExtraDays)), any())
        verify(mockAppRepository).removeProgressStatuses(eqTo(applicationId), eqTo(statusToRemoveWhenExpiryInMoreThanThreeDays))
      }
    }
    "return a failed Future" when {
      "the application status doesn't allow an extension" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getTestGroup(applicationId)).thenReturnAsync(successfulTestProfile)
        whenReady(underTest.extendTestGroupExpiryTime(applicationId, twoExtraDays, "triggeredBy").failed) { e =>
          e mustBe (invalidStatusError)
        }

        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockOtRepository).getTestGroup(eqTo(applicationId))
        verifyNoMoreInteractions(mockAppRepository, mockOtRepository, mockAuditService, mockDateFactory)
      }
      "find progress fails" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturn(Future.failed(genericError))
        whenReady(underTest.extendTestGroupExpiryTime(applicationId, twoExtraDays, "triggeredBy").failed) { e =>
          e mustBe (genericError)
        }
        verify(mockAppRepository).findProgress(eqTo(applicationId))
      }
      "No test phase 1 profile is available" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getTestGroup(applicationId)).thenReturnAsync(None)
        whenReady(underTest.extendTestGroupExpiryTime(applicationId, twoExtraDays, "triggeredBy").failed) { e =>
          e mustBe (noTestProfileFoundError)
        }
        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockOtRepository).getTestGroup(eqTo(applicationId))
      }
      "remove status return an error and no audit event is emitted" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturnAsync(successfulProgressResponse)
        when(mockOtRepository.getTestGroup(applicationId)).thenReturnAsync(successfulTestProfile)
        when(mockProgressResponse.phase1ProgressResponse.phase1TestsExpired).thenReturn(true)
        when(mockDateFactory.nowLocalTimeZone).thenReturn(Now)
        when(mockProfile.expirationDate).thenReturn(OneHourAgo)
        when(mockOtRepository.updateGroupExpiryTime(eqTo(applicationId), any(), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturn(Future.failed(genericError))

        whenReady(underTest.extendTestGroupExpiryTime(applicationId, twoExtraDays, "triggeredBy").failed) { e =>
          e mustBe (genericError)
        }

        verify(mockAppRepository).removeProgressStatuses(eqTo(applicationId), eqTo(statusToRemoveWhenExpiryInMoreThanOneDayExpired))
      }
    }
  }

  "getProgressStatusesToRemove" should {
    "return a list of status to remove" when {
      import OnlineTestExtensionServiceImpl.getProgressStatusesToRemove
      "the new expiry date is more than 3 days ahead" in new TestFixture {
         val result = getProgressStatusesToRemove(InMoreThanThreeDays, mockProfile, mockProgressResponse)
         result mustBe(Some(statusToRemoveWhenExpiryInMoreThanThreeDays))
      }
      "the new expiry date is more than 3 days ahead and the test was expired and not started" in new TestFixture {
        when(mockProfile.hasNotStartedYet).thenReturn(true)
        when(mockProgressResponse.phase1ProgressResponse.phase1TestsExpired).thenReturn(true)

        val result = getProgressStatusesToRemove(InMoreThanThreeDays, mockProfile, mockProgressResponse)
        result mustBe(Some(statusToRemoveWhenExpiryInMoreThanThreeDaysExpiredNotStarted))
      }
      "the new expiry date is more than 1 day but less than 3 days ahead" in new TestFixture {
        val result = getProgressStatusesToRemove(InTwentyFiveHours, mockProfile, mockProgressResponse)
        result mustBe(Some(statusToRemoveWhenExpiryInMoreThanOneDay))
      }
      "the new expiry date is less than 1 day ahead" in new TestFixture {
        val result = getProgressStatusesToRemove(InFiveHours, mockProfile, mockProgressResponse)
        result mustBe(None)
      }
    }
  }

  trait TestFixture {
    implicit val hc = HeaderCarrier()
    implicit val rh = mock[RequestHeader]
    val applicationId = "abc"
    val twoExtraDays = 2
    val threeExtraDays = 3
    val statusToRemoveWhenNotStartedAndFirstReminderSent = List(PHASE1_TESTS_STARTED, PHASE1_TESTS_FIRST_REMINDER)
    val statusToRemoveWhenExpiryInMoreThanOneDayExpired = List(PHASE1_TESTS_EXPIRED, PHASE1_TESTS_SECOND_REMINDER)
    val statusToRemoveWhenExpiryInMoreThanThreeDays = List(PHASE1_TESTS_SECOND_REMINDER, PHASE1_TESTS_FIRST_REMINDER)
    val statusToRemoveWhenExpiryInMoreThanOneDay = List(PHASE1_TESTS_SECOND_REMINDER)
    val statusToRemoveWhenExpiryInMoreThanThreeDaysExpiredNotStarted = List(
      PHASE1_TESTS_EXPIRED, PHASE1_TESTS_STARTED, PHASE1_TESTS_SECOND_REMINDER, PHASE1_TESTS_FIRST_REMINDER)
    val invalidStatusError = new TestExtensionException("Application is in an invalid status for test extension")
    val noTestProfileFoundError = TestExtensionException("No Phase1TestGroupAvailable for the given application")
    val genericError = new Exception("Dummy error!")
    val mockDateFactory = mock[DateTimeFactory]
    val Now = DateTime.now()
    val OneHourAgo = Now.minusHours(1).minusSeconds(1)
    val InFiveHours = Now.plusHours(5).plusSeconds(1)
    val InTwentyFiveHours = Now.plusHours(25).plusSeconds(1)
    val InMoreThanThreeDays = Now.plusHours(73).plusSeconds(1)
    val mockProfile = mock[Phase1TestProfile]
    val mockPhase1ProgressResponse = mock[Phase1ProgressResponse]
    val mockProgressResponse = mock[ProgressResponse]
    val successfulProgressResponse = mockProgressResponse
    val successfulTestProfile = Some(mockProfile)
    val mockAppRepository = mock[GeneralApplicationRepository]
    val mockOtRepository = mock[Phase1TestRepository]
    val mockAuditService = mock[AuditService]
    val underTest = new OnlineTestExtensionService with EventServiceFixture {
      val appRepository = mockAppRepository
      val otRepository = mockOtRepository
      val auditService = mockAuditService
      val dateTimeFactory = mockDateFactory
    }

    when(mockProgressResponse.phase1ProgressResponse).thenReturn(mockPhase1ProgressResponse)
  }
}
