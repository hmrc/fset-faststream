package services.events

import model.persisted.eventschedules._
import org.scalatest.concurrent.ScalaFutures
import repositories.EventRepositoryImpl
import services.BaseServiceSpec
import org.joda.time.{ LocalDate, LocalTime }

class EventsRepositorySpec extends BaseServiceSpec with ScalaFutures {

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
                    |  minViable: 12
                    |  safetyMargin: 2
                    |  assessors: 6
                    |  chairs: 3
                    |  deptAssessors: 3
                    |  exerciseMarkers: 3
                    |  qacs: 1
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
                    |  minViable: 12
                    |  safetyMargin: 2
                    |  assessors: 6
                    |  chairs: 3
                    |  deptAssessors: 3
                    |  exerciseMarkers: 2
                    |  qacs: 1
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
                    |  minViable: 12
                    |  safetyMargin: 2
                    |  assessors: 6
                    |  chairs: 3
                    |  deptAssessors: 2
                    |  exerciseMarkers: 3
                    |  qacs: 1
                    |  sessions:
                    |    - desc: First
                    |      start: 9:00
                    |      end: 10:30
                    |    - desc: Second
                    |      start: 10:30
                    |      end: 12:00""".stripMargin

      val repo = new EventRepositoryImpl {
        private lazy val rawConfig = input
      }

      whenReady(repo.events) { result =>
        // scalastyle:off
        result mustBe List(Event("FSEC",
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
        // scalastyle:on
      }
    }
  }
}
