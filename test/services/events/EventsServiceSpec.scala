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

package services.events

import connectors.AuthProviderClient
import connectors.ExchangeObjects.Candidate
import model.persisted.EventExamples
import model.persisted.assessor.AssessorExamples
import model.stc.EmailEvents.AssessorNewEventCreated
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.Mockito.when
import play.api.mvc.RequestHeader
import repositories.events.{ EventsConfigRepository, EventsRepository }
import services.allocation.{ AssessorAllocationService, CandidateAllocationService }
import services.assessoravailability.AssessorService
import services.stc.{ StcEventService, StcEventServiceMock }
import testkit.MockitoImplicits._
import testkit.{ ExtendedTimeout, MockitoSugar, UnitWithAppSpec }
import uk.gov.hmrc.play.http.HeaderCarrier

class EventsServiceSpec extends UnitWithAppSpec with ExtendedTimeout with MockitoSugar {


  "save event" should {
    "send emails to all assessors that didn't submit availability" in new TestFixture {

      val e = EventExamples.e1WithSkills
      val a = AssessorExamples.AssessorExisting
      val c = Candidate("Bob ", "Smith", None, "bob@mailinator.com", "1")

      when(mockEventsRepo.save(List(e))).thenReturnAsync()
      when(mockAssessorService.findAssessorsNotAvailableOnDay(List("ASSESSOR"), e.date, e.location))
        .thenReturnAsync(Seq(a))
      when(mockAuthProviderClient.findByUserIds(Seq(a.userId))).thenReturnAsync(Seq(c))

      service.save(e).futureValue

      eventServiceStub.cache mustBe List(List(AssessorNewEventCreated(c.email, c.name, service.renderLongDate(e.date))))
    }
  }

  trait TestFixture extends StcEventServiceMock {

    implicit val hc = mock[HeaderCarrier]
    implicit val rh = mock[RequestHeader]

    val mockEventsRepo = mock[EventsRepository]
    val mockAssessorService = mock[AssessorService]
    val mockAuthProviderClient = mock[AuthProviderClient]
    val mockAssessorAllocationService = mock[AssessorAllocationService]
    val mockCandidateAllocationService = mock[CandidateAllocationService]
    val mockEventsConfigRepo = mock[EventsConfigRepository]

    val service = new EventsService {
      override def eventsRepo: EventsRepository = mockEventsRepo

      override def assessorService: AssessorService = mockAssessorService

      override def authProviderClient: AuthProviderClient = mockAuthProviderClient

      override def assessorAllocationService: AssessorAllocationService = mockAssessorAllocationService

      override def candidateAllocationService: CandidateAllocationService = mockCandidateAllocationService

      override def eventsConfigRepo: EventsConfigRepository = mockEventsConfigRepo

      override val eventService: StcEventService = eventServiceStub
    }
  }
}
