package services.assessmentcentre

import repositories.assessmentcentre.AssessmentEventsRepository
import services.BaseServiceSpec

import scala.concurrent.Future

/**
  * Created by fayimora on 08/06/2017.
  */
class AssessmentCentreParsingServiceSpec extends BaseServiceSpec {
  "processCentres" must {
    "successfully saves and loads the file contents" in new TestFixture {
      val events = service.processCentres().futureValue

      events.size mustBe 2
      events.head.eventType mustBe "FSAC"
      events(1).skillRequirements.getOrElse("QUALITY_ASSURANCE_COORDINATOR", "--") mustBe 0
    }
  }

  trait TestFixture {
    val mockAssessmentCentreRepository = mock[AssessmentEventsRepository]
    val service = new AssessmentCentreParsingService {
      val fileContents = Future.successful(List(
        "FSAC,London,London (Euston Tower),03/04/17,09:00,12:00,36,4,5,6,1,1,1,1,2",
        "SDIP telephone interview,London,London (Euston Tower),04/04/17,08:00,13:30,36,24,7,,,,,,"
      ))

      val assessmentCentreRepository = mockAssessmentCentreRepository
    }
  }
}
