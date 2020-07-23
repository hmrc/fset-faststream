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

package services.sift

import factories.DateTimeFactory
import model.ProgressStatuses._
import model.command.{ ProgressResponse, SiftProgressResponse }
import model.persisted.sift.SiftTestGroup
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito.{ never, verify, verifyNoMoreInteractions, when }
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.sift.ApplicationSiftRepository
import services.stc.StcEventServiceFixture
import testkit.MockitoImplicits.{ OngoingStubbingExtension, OngoingStubbingExtensionUnit }
import testkit.{ ShortTimeout, UnitSpec }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class SiftExpiryExtensionServiceSpec extends UnitSpec with ShortTimeout {

  "extendExpiryTime" must {
    "return a successful Future" when {
      "adding extra 2 days onto expiry and candidate is expired and has received both reminders" in new TestFixture {
        when(mockAppRepository.findProgress(applicationId)).thenReturnAsync(
          // We are adding 2 days onto expiry. Both reminders have been sent out
          createProgressResponse(SiftProgressResponse(
            siftEntered = true, siftExpired = true, siftExpiredNotified = true, siftFirstReminder = true, siftSecondReminder = true)
          )
        )
        when(mockSiftRepository.getTestGroup(applicationId)).thenReturnAsync(siftTestGroup)

        when(mockDateTimeFactory.nowLocalTimeZone).thenReturn(now)
        when(mockSiftTestGroup.expirationDate).thenReturn(oneHourAgo)

        when(mockSiftRepository.updateExpiryTime(eqTo(applicationId), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturnAsync()

        service.extendExpiryTime(applicationId, twoDays, "triggeredBy").futureValue

        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockSiftRepository).getTestGroup(eqTo(applicationId))
        verify(mockDateTimeFactory).nowLocalTimeZone
        verify(mockSiftRepository).updateExpiryTime(eqTo(applicationId), eqTo(now.plusDays(twoDays)))
        // Expect sift expired and both reminder statuses to be removed
        verify(mockAppRepository).removeProgressStatuses(eqTo(applicationId),
          eqTo(List(SIFT_EXPIRED_NOTIFIED, SIFT_EXPIRED, SIFT_SECOND_REMINDER, SIFT_FIRST_REMINDER))
        )

        verifyAuditEvents(1, "ExpiredSiftExtended")
        verifyDataStoreEvents(1, "SiftNumericExerciseExtended")
      }

      "adding extra 2 days onto expiry and candidate is not expired and has only received the first reminder" in new TestFixture {
        when(mockAppRepository.findProgress(applicationId)).thenReturnAsync(
          // We are adding 2 days onto expiry. Only first reminder has been sent out
          createProgressResponse(SiftProgressResponse(
            siftEntered = true, siftFirstReminder = true)
          )
        )
        when(mockSiftRepository.getTestGroup(applicationId)).thenReturnAsync(siftTestGroup)

        when(mockSiftTestGroup.expirationDate).thenReturn(inTwoDays)

        when(mockSiftRepository.updateExpiryTime(eqTo(applicationId), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturnAsync()

        service.extendExpiryTime(applicationId, twoDays, "triggeredBy").futureValue

        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockSiftRepository).getTestGroup(eqTo(applicationId))
        verify(mockSiftRepository).updateExpiryTime(eqTo(applicationId), eqTo(inTwoDays.plusDays(twoDays)))
        // Expect the first reminder status to be removed
        verify(mockAppRepository).removeProgressStatuses(eqTo(applicationId),
          eqTo(List(SIFT_FIRST_REMINDER))
        )

        verifyAuditEvents(1, "NonExpiredSiftExtended")
        verifyDataStoreEvents(1, "SiftNumericExerciseExtended")
      }

      "processing a candidate who is in SIFT_ENTERED progress status and has not been sent any reminders" in new TestFixture {
        when(mockAppRepository.findProgress(applicationId)).thenReturnAsync(
          // We do not expect any statuses to be removed - the candidate should just still be in sift entered
          createProgressResponse(SiftProgressResponse(siftEntered = true))
        )
        when(mockSiftRepository.getTestGroup(applicationId)).thenReturnAsync(siftTestGroup)
        when(mockSiftTestGroup.expirationDate).thenReturn(inTenDays)

        when(mockDateTimeFactory.nowLocalTimeZone).thenReturn(now)
        when(mockSiftTestGroup.expirationDate).thenReturn(now.plusDays(tenDays)) // current expiry date is 10 days from now

        when(mockSiftRepository.updateExpiryTime(eqTo(applicationId), any())).thenReturnAsync()
        // Not expecting this to be called so set it up to throw an exception
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturn(Future.failed(dummyError))

        service.extendExpiryTime(applicationId, twoDays, "triggeredBy").futureValue // extend the expiry date by 2 days

        verifyAuditEvents(1, "NonExpiredSiftExtended")
        verifyDataStoreEvents(1, "SiftNumericExerciseExtended")

        verify(mockSiftRepository).updateExpiryTime(eqTo(applicationId), eqTo(inTenDays.plusDays(twoDays)))

        // Should not be called because the list of progress statuses to remove should be empty
        verify(mockAppRepository, never()).removeProgressStatuses(any(), any())
      }
    }

    "return a failed Future" when {
      "the application status doesn't allow an extension" in new TestFixture {
        when(mockAppRepository.findProgress(applicationId)).thenReturnAsync(createProgressResponse())
        when(mockSiftRepository.getTestGroup(applicationId)).thenReturnAsync(siftTestGroup)

        whenReady(service.extendExpiryTime(applicationId, twoDays, "triggeredBy").failed) { e =>
          e mustBe SiftExtensionException("Application is in an invalid state for sift extension")
        }

        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockSiftRepository).getTestGroup(eqTo(applicationId))
        verifyNoMoreInteractions(mockAppRepository, mockSiftRepository, auditEventHandlerMock, dataStoreEventHandlerMock)
      }

      "find progress method fails" in new TestFixture {
        when(mockAppRepository.findProgress(any())).thenReturn(Future.failed(dummyError))

        whenReady(service.extendExpiryTime(applicationId, twoDays, "triggeredBy").failed) { e =>
          e mustBe dummyError
        }
        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verifyNoMoreInteractions(mockAppRepository, mockSiftRepository, auditEventHandlerMock, dataStoreEventHandlerMock)
      }

      "No sift test group is available" in new TestFixture {
        when(mockAppRepository.findProgress(applicationId)).thenReturnAsync(createProgressResponse())
        when(mockSiftRepository.getTestGroup(applicationId)).thenReturnAsync(None)
        whenReady(service.extendExpiryTime(applicationId, twoDays, "triggeredBy").failed) { e =>
          e mustBe SiftExtensionException("No Sift test group available for the given application")
        }
        verify(mockAppRepository).findProgress(eqTo(applicationId))
        verify(mockSiftRepository).getTestGroup(eqTo(applicationId))
      }

      "remove progress statuses returns an error and no audit event is emitted" in new TestFixture {
        when(mockAppRepository.findProgress(applicationId)).thenReturnAsync(
          createProgressResponse(SiftProgressResponse(siftEntered = true, siftExpired = true, siftExpiredNotified = true))
        )
        when(mockSiftRepository.getTestGroup(applicationId)).thenReturnAsync(siftTestGroup)

        when(mockDateTimeFactory.nowLocalTimeZone).thenReturn(now)
        when(mockSiftTestGroup.expirationDate).thenReturn(oneHourAgo)

        when(mockSiftRepository.updateExpiryTime(eqTo(applicationId), any())).thenReturnAsync()
        when(mockAppRepository.removeProgressStatuses(eqTo(applicationId), any())).thenReturn(Future.failed(dummyError))

        whenReady(service.extendExpiryTime(applicationId, twoDays, "triggeredBy").failed) { e =>
          e mustBe dummyError
        }

        verify(mockAppRepository).removeProgressStatuses(eqTo(applicationId), eqTo(List(SIFT_EXPIRED_NOTIFIED, SIFT_EXPIRED)))
        verifyNoMoreInteractions(auditEventHandlerMock, dataStoreEventHandlerMock)
      }
    }
  }

  "getProgressStatusesToRemove" should {
    "return a list of statuses to remove" when {
      import SiftExpiryExtensionServiceImpl.getProgressStatusesToRemove
      "the progress response indicates we are not in sift" in new TestFixture {
        when(mockProgressResponse.siftProgressResponse).thenReturn(new SiftProgressResponse)

        val result = getProgressStatusesToRemove(mockProgressResponse)
        result mustBe None
      }

      "the progress response indicates we are expired and have received all reminders" in new TestFixture {
        when(mockProgressResponse.siftProgressResponse).thenReturn(SiftProgressResponse(
          siftEntered = true, siftFirstReminder = true, siftSecondReminder = true, siftExpired = true, siftExpiredNotified = true
        ))

        val result = getProgressStatusesToRemove(mockProgressResponse)
        result mustBe Some(List(SIFT_EXPIRED_NOTIFIED, SIFT_EXPIRED, SIFT_SECOND_REMINDER, SIFT_FIRST_REMINDER))
      }

      "the progress response indicates we have only received the first reminder" in new TestFixture {
        when(mockProgressResponse.siftProgressResponse).thenReturn(SiftProgressResponse(
          siftEntered = true, siftFirstReminder = true
        ))

        val result = getProgressStatusesToRemove(mockProgressResponse)
        result mustBe Some(List(SIFT_FIRST_REMINDER))
      }

      "the progress response indicates we have not expired and have received both reminders" in new TestFixture {
        when(mockProgressResponse.siftProgressResponse).thenReturn(SiftProgressResponse(
          siftEntered = true, siftFirstReminder = true, siftSecondReminder = true
        ))

        val result = getProgressStatusesToRemove(mockProgressResponse)
        result mustBe Some(List(SIFT_SECOND_REMINDER, SIFT_FIRST_REMINDER))
      }
    }
  }

  trait TestFixture extends StcEventServiceFixture {
    implicit val hc = HeaderCarrier()
    implicit val rh = mock[RequestHeader]
    val applicationId = "appId"
    val twoDays = 2
    val tenDays = 10

    val dummyError = new Exception("Dummy error for test")

    val now = DateTime.now()
    val oneHourAgo = now.minusHours(1)
    val inTwoDays = now.plusDays(2)
    val inTenDays = now.plusDays(10)

    def createProgressResponse(siftProgress: SiftProgressResponse = SiftProgressResponse()): ProgressResponse =
      ProgressResponse(applicationId, siftProgressResponse = siftProgress)

    val mockSiftTestGroup = mock[SiftTestGroup]
    val siftTestGroup = Some(mockSiftTestGroup)

    val mockAppRepository = mock[GeneralApplicationRepository]
    val mockSiftRepository = mock[ApplicationSiftRepository]
    val mockDateTimeFactory = mock[DateTimeFactory]

    val service = new SiftExpiryExtensionService(
      mockAppRepository,
      mockSiftRepository,
      mockDateTimeFactory,
      stcEventServiceMock
    )

    val mockProgressResponse = mock[ProgressResponse]
   }
}
