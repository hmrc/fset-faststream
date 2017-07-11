package services.events

import model.persisted.eventschedules._
import org.scalatest.concurrent.ScalaFutures
import repositories.events.EventsConfigRepository
import services.BaseServiceSpec
import org.joda.time.{ LocalDate, LocalTime }
import org.scalatest.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.play.test.UnitSpec

class EventsConfigRepositorySpec extends UnitSpec with Matchers with ScalaFutures {
  "events" must {
    "successfully parse the config" in {
      val input = """- type: FSAC
                    |  desc: PDFS FSB
                    |  location: London
                    |  venue: LONDON_FSAC
                    |  date: 2017-04-03
                    |  start: 11:00
                    |  end: 12:00
                    |  capacity: 36
                    |  minViableAttendees: 12
                    |  attendeeSafetyMargin: 2
                    |  resourceRequirements:
                    |    - ASSESSOR: 6
                    |    - CHAIR: 3
                    |    - DEPARTMENTAL_ASSESSOR: 3
                    |    - EXERCISE_MARKER: 3
                    |    - QUALITY_ASSURANCE_COORDINATOR: 1
                    |  sessions:
                    |    - desc: AM
                    |      start: 11:00
                    |      end: 12:00
                    |- type: FSAC
                    |  desc: PDFS FSB
                    |  location: London
                    |  venue: LONDON_FSAC
                    |  date: 2017-04-03
                    |  start: 9:00
                    |  end: 12:00
                    |  capacity: 36
                    |  minViableAttendees: 12
                    |  attendeeSafetyMargin: 2
                    |  resourceRequirements:
                    |    - ASSESSOR: 6
                    |    - CHAIR: 3
                    |    - DEPARTMENTAL_ASSESSOR: 3
                    |    - EXERCISE_MARKER: 2
                    |    - QUALITY_ASSURANCE_COORDINATOR: 1
                    |  sessions:
                    |    - desc: First
                    |      start: 9:00
                    |      end: 10:30
                    |    - desc: Second
                    |      start: 10:30
                    |      end: 12:00
                    |- type: FSAC
                    |  desc: PDFS FSB
                    |  location: Newcastle
                    |  venue: NEWCASTLE_LONGBENTON
                    |  date: 2017-04-03
                    |  start: 09:00
                    |  end: 12:00
                    |  capacity: 36
                    |  minViableAttendees: 12
                    |  attendeeSafetyMargin: 2
                    |  resourceRequirements:
                    |    - ASSESSOR: 6
                    |    - CHAIR: 3
                    |    - DEPARTMENTAL_ASSESSOR: 2
                    |    - EXERCISE_MARKER: 3
                    |    - QUALITY_ASSURANCE_COORDINATOR: 1
                    |  sessions:
                    |    - desc: First
                    |      start: 9:00
                    |      end: 10:30
                    |    - desc: Second
                    |      start: 10:30
                    |      end: 12:00""".stripMargin

      val repo = new EventsConfigRepository {
        override protected def rawConfig = input
      }

      implicit val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

      whenReady(repo.events) { result =>
        // scalastyle:off parameter.number
        result shouldBe List(Event("FSEC",
          EventType.withName("FSAC"),
          "",
          Location("London"),
          Venue("LONDON_FSAC", ""),
          LocalDate.parse("2017-04-03"),
          36, 12, 2,
          LocalTime.parse("11:00"),
          LocalTime.parse("12:00"),
          Map("assessors" -> 6, "chairs" -> 3, "deptAssessors" -> 3, "exerciseMarkers" -> 3, "qacs" -> 1),
          List(Session("AM", LocalTime.parse("11:00"), LocalTime.parse("12:00")))))
        // scalastyle:on parameter.number
      }
    }
  }
}
