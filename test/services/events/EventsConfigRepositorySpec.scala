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

package services.events

import model.persisted.EventExamples
import model.persisted.eventschedules._
import org.scalatest.concurrent.ScalaFutures
import repositories.events.{ EventsConfigRepository, LocationsWithVenuesRepository }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }
import persisted.FsbTypeExamples
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class EventsConfigRepositorySpec extends UnitSpec with Matchers with ScalaFutures with testkit.MockitoSugar {
  "events" must {
    "successfully parse the event schedule config" in {
      val input =
        """- eventType: FSAC
          |  description: PDFS FSB
          |  location: London
          |  venue: LONDON_FSAC
          |  date: 2017-04-03
          |  capacity: 36
          |  minViableAttendees: 12
          |  attendeeSafetyMargin: 2
          |  startTime: 11:00
          |  endTime: 12:00
          |  skillRequirements:
          |    ASSESSOR: 6
          |    CHAIR: 3
          |    DEPARTMENTAL_ASSESSOR: 3
          |    EXERCISE_MARKER: 3
          |    QUALITY_ASSURANCE_COORDINATOR: 1
          |  sessions:
          |    - description: AM
          |      capacity: 36
          |      minViableAttendees: 12
          |      attendeeSafetyMargin: 4
          |      startTime: 11:00
          |      endTime: 12:00
          |- eventType: FSAC
          |  description: PDFS FSB
          |  location: London
          |  venue: LONDON_FSAC
          |  date: 2017-04-03
          |  capacity: 36
          |  minViableAttendees: 12
          |  attendeeSafetyMargin: 2
          |  startTime: 9:00
          |  endTime: 12:00
          |  skillRequirements:
          |    ASSESSOR: 6
          |    CHAIR: 3
          |    DEPARTMENTAL_ASSESSOR: 3
          |    EXERCISE_MARKER: 2
          |    QUALITY_ASSURANCE_COORDINATOR: 1
          |  sessions:
          |    - description: First
          |      capacity: 36
          |      minViableAttendees: 12
          |      attendeeSafetyMargin: 4
          |      startTime: 9:00
          |      endTime: 10:30
          |    - description: Second
          |      capacity: 36
          |      minViableAttendees: 12
          |      attendeeSafetyMargin: 4
          |      startTime: 10:30
          |      endTime: 12:00
          |- eventType: FSAC
          |  description: PDFS FSB
          |  location: Newcastle
          |  venue: NEWCASTLE_LONGBENTON
          |  date: 2017-04-03
          |  capacity: 36
          |  minViableAttendees: 12
          |  attendeeSafetyMargin: 2
          |  startTime: 09:00
          |  endTime: 12:00
          |  skillRequirements:
          |    ASSESSOR: 6
          |    CHAIR: 3
          |    DEPARTMENTAL_ASSESSOR: 2
          |    EXERCISE_MARKER: 3
          |    QUALITY_ASSURANCE_COORDINATOR: 1
          |  sessions:
          |    - description: First
          |      capacity: 36
          |      minViableAttendees: 12
          |      attendeeSafetyMargin: 4
          |      startTime: 9:00
          |      endTime: 10:30
          |    - description: Second
          |      capacity: 36
          |      minViableAttendees: 12
          |      attendeeSafetyMargin: 4
          |      startTime: 10:30
          |      endTime: 12:00""".stripMargin

      val mockLocationsWithVenuesRepo = mock[LocationsWithVenuesRepository]
      when(mockLocationsWithVenuesRepo.venue(any[String])).thenReturn(Future.successful(Venue("london fsac", "bush house")))
      when(mockLocationsWithVenuesRepo.location(any[String])).thenReturn(Future.successful(Location("London")))

      val repo = new EventsConfigRepository {
        override protected def eventScheduleConfig: String = input

        override def locationsWithVenuesRepo: LocationsWithVenuesRepository = mockLocationsWithVenuesRepo
      }

      implicit val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

      def withDefaultFields(event: Event) = {
          event.copy(id = "e1", createdAt = EventExamples.eventCreatedAt, sessions = event.sessions.map { session =>
              session.copy(id = "s1")
            }, wasBulkUploaded = true
          )
      }

      whenReady(repo.events) { result =>
        result.zip(EventExamples.YamlEvents).foreach { case (actual, expected) =>
          withDefaultFields(actual) shouldBe withDefaultFields(expected)
        }
      }
    }
  }
}
