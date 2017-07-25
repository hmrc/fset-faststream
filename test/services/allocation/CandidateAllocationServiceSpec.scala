/*
 * Copyright 2017 HM Revenue & Customs
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

package services.allocation

import connectors.{ AuthProviderClient, EmailClient }
import connectors.ExchangeObjects.Candidate
import model.AllocationStatuses
import model.command.{ CandidateAllocation, CandidateAllocations }
import model.persisted.EventExamples
import model.persisted.eventschedules.{ Event, EventType, Location, Venue }
import org.joda.time.{ LocalDate, LocalTime }
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import repositories.CandidateAllocationMongoRepository
import repositories.application.GeneralApplicationRepository
import services.BaseServiceSpec
import services.events.EventsService
import services.stc.StcEventService
import uk.gov.hmrc.play.http.HeaderCarrier
import testkit.MockitoImplicits._
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import scala.concurrent.Future

class CandidateAllocationServiceSpec extends BaseServiceSpec {
  "Allocate candidate" must {
    "save allocation if non already exists" in new TestFixture {
      val eventId = "E1"
      val sessionId = "S1"
      val appId = "app1"
      val candidateAllocations = CandidateAllocations("v1", eventId, sessionId, Seq(CandidateAllocation(appId, AllocationStatuses.UNCONFIRMED)))

      when(mockEventsService.getEvent(eventId)).thenReturn(Future.successful(EventExamples.e1))
      when(mockCandidateAllocationRepository.allocationsForSession(eventId, sessionId)).thenReturn(Future.successful(Nil))
      when(mockAppRepo.find(appId)).thenReturn(Future.successful(None))
      service.allocateCandidates(candidateAllocations)
    }
  }


  trait TestFixture {
    val mockCandidateAllocationRepository: CandidateAllocationMongoRepository = mock[CandidateAllocationMongoRepository]
    val mockAppRepo: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    val mockEventsService: EventsService = mock[EventsService]
    val mockEmailClient: EmailClient = mock[EmailClient]
    val mockAuthProviderClient: AuthProviderClient = mock[AuthProviderClient]
    val mockStcEventService: StcEventService = mock[StcEventService]

    val service = new CandidateAllocationService {
      override val eventsService: EventsService = mockEventsService
      override val applicationRepo: GeneralApplicationRepository = mockAppRepo
      override def emailClient: EmailClient = mockEmailClient
      override def authProviderClient: AuthProviderClient = mockAuthProviderClient
      override val eventService: StcEventService = mockStcEventService
      def candidateAllocationRepo: CandidateAllocationMongoRepository = mockCandidateAllocationRepository
    }

    protected def mockGetEvent: OngoingStubbing[Future[Event]] = when(mockEventsService.getEvent(any[String]())).thenReturnAsync(new Event(
      "eventId", EventType.FSAC, "Description", Location("London"), Venue("Venue 1", "venue description"),
      LocalDate.now, 10, 10, 10, LocalTime.now, LocalTime.now, Map(), Nil
    ))

    protected def mockAuthProviderFindByUserIds(userId: String*): Unit = userId.foreach { uid =>
      when(mockAuthProviderClient.findByUserIds(eqTo(Seq(uid)))(any[HeaderCarrier]())).thenReturnAsync(
        Seq(
          Candidate("Bob " + uid, "Smith", None, "bob@mailinator.com", uid)
        )
      )
    }
  }
}
