package services.assessmentcentre

import model.persisted.assessmentcentre.Event
import services.BaseServiceSpec

import scala.concurrent.Future

/**
  * Created by fayimora on 08/06/2017.
  */
class AssessmentCentreParsingServiceSpec extends BaseServiceSpec {
  "processCentres" must {
    "successfully saves and loads the file contents" in new GoodTestFixture {
      val events: Seq[Event] = service.processCentres().futureValue

      events.size mustBe 2
      events.head.eventType mustBe "FSAC"
      events(1).skillRequirements.getOrElse("QUALITY_ASSURANCE_COORDINATOR", "--") mustBe 0
    }

    "fails gracefully when any field is malformed" in new MalformedTestFixture {
      an[Exception] must be thrownBy service.processCentres().futureValue
    }
  }


  trait MalformedTestFixture {
    val service = new AssessmentCentreParsingService {
      val fileContents: Future[List[String]] = Future.successful(List(
        "fsac,london,london (euston tower),03/04/17,09:0,12:00,36,4,5,6,1,1,1,1,2", // malformed starttime
        "sdip telephone interview,london,london (euston tower),,08:00,13:30,36,24,7,,,,,," // missing date
      ))
    }
  }

  trait GoodTestFixture {
    val service = new AssessmentCentreParsingService {
      val fileContents: Future[List[String]] = Future.successful(List(
        "FSAC,London,London (Euston Tower),03/04/17,09:00,12:00,36,4,5,6,1,1,1,1,2",
        "SDIP telephone interview,London,London (Euston Tower),04/04/17,08:00,13:30,36,24,7,,,,,,"
      ))
    }
  }
}
