package services

import org.scalatest.mock.MockitoSugar
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import services.application.ApplicationService
import services.events.EventService
import testkit.MongoRepositorySpec


class ApplicationServiceSpec extends MongoRepositorySpec with MockitoSugar {

  lazy val service = new ApplicationService {
    val appRepository: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    val eventService: EventService = mock[EventService]
    val pdRepository: PersonalDetailsRepository = mock[PersonalDetailsRepository]
    val cdRepository: ContactDetailsRepository = mock[ContactDetailsRepository]
  }

  val collectionName = "application"
  val DebugTestNameAppId: Option[String] = None


  "ApplicationService" should {
    "do something" in {

    }
  }

}
