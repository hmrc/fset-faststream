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

package scheduler.allocation

import model.PersistedObjects.{ AllocatedCandidate, PersonalDetailsWithUserId }
import org.joda.time.LocalDate
import org.mockito.Mockito._
import play.api.test.WithApplication
import services.allocation.CandidateAllocationService
import testkit.UnitWithAppSpec

import scala.concurrent.{ ExecutionContext, Future }

class ConfirmAttendanceReminderJobSpec extends UnitWithAppSpec {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val candidate = AllocatedCandidate(PersonalDetailsWithUserId("Alice", "userId"), "app1", LocalDate.now().plusDays(1))

  "Confirm attendance reminder job" should {
    "send an reminder email when there is one candidate who has unconfirmed attendance" in new TestFixture {
      when(candidateAllocationServiceMock.nextUnconfirmedCandidateForSendingReminder)
        .thenReturn(Future.successful(Some(candidate)))
      when(candidateAllocationServiceMock.sendEmailConfirmationReminder(candidate)).thenReturn(Future.successful(()))

      job.tryExecute().futureValue must be(())

      verify(candidateAllocationServiceMock).sendEmailConfirmationReminder(candidate)
    }

    "do not send any email when there is no unconfirmed attendance" in new TestFixture {
      when(candidateAllocationServiceMock.nextUnconfirmedCandidateForSendingReminder)
        .thenReturn(Future.successful(None))

      job.tryExecute().futureValue must be(())

      verify(candidateAllocationServiceMock, never).sendEmailConfirmationReminder(candidate)
    }
  }

  trait TestFixture extends {
    val candidateAllocationServiceMock = mock[CandidateAllocationService]

    def job = new ConfirmAttendanceReminderJob {
      val candidateAllocationService = candidateAllocationServiceMock
      val config = ConfirmAttendanceReminderJobConfig
    }
  }
}
