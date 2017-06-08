package services.assessmentcentre

import repositories.assessmentcentre.AssessmentEventsRepository
import services.BaseServiceSpec

import scala.concurrent.Future

/**
  * Created by fayimora on 08/06/2017.
  */
class AssessmentCentreParsingServiceSpec extends BaseServiceSpec {
  "processCentres" must {
    "successfully save the file contents" in new TestFixture {
      val events = service.processCentres().futureValue
      events.size mustBe 2
      // TODO: Don't stop here!
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
