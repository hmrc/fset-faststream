package repositories.event

import model.Exceptions.EventNotFoundException
import repositories.CollectionNames
import model.persisted.EventExamples
import model.persisted.eventschedules.{ EventType, SkillType, VenueType }
import testkit.MongoRepositorySpec

class EventsRepositorySpec extends MongoRepositorySpec {

  override def beforeAll(): Unit = {
    super.beforeAll()
    repository.save(EventExamples.EventsNew).futureValue
  }

  override def afterAll(): Unit = {
    super.beforeEach()
    super.afterAll()
  }

  override def beforeEach(): Unit = {}

  override val collectionName: String = CollectionNames.ASSESSMENT_EVENTS
  lazy val repository = repositories.eventsRepository

  "Events" should {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repository)
      indexes must contain theSameElementsAs
        Seq(List("eventType", "date", "location", "venue"), List("_id"))
    }

    "save and fetch events" in {
      val result = repository.getEvents(Some(EventType.FSAC), Some(EventExamples.VenueLondon)).futureValue
      result.size mustBe 2
    }

    "filter FSAC in LONDON_FSAC events" in {
      val result = repository.getEvents(Some(EventType.FSAC), Some(EventExamples.VenueLondon)).futureValue
      result.size mustBe 2
      result.contains(EventExamples.EventsNew.head) mustBe true
      result.contains(EventExamples.EventsNew.tail.head) mustBe true
    }

    "filter SKYPE_INTERVIEW in NEWCASTLE_LONGBENTON " in {
      val result = repository.getEvents(Some(EventType.SKYPE_INTERVIEW), Some(EventExamples.VenueNewcastle)).futureValue
      result.size mustBe 1
      result.head.venue mustBe EventExamples.VenueNewcastle
    }

    "filter by skills" in {
      val result = repository.getEvents(None, None, None, List(SkillType.QUALITY_ASSURANCE_COORDINATOR)).futureValue

      result.size mustBe 1
      result.head.venue mustBe EventExamples.VenueNewcastle

    }

    "filter by skills and Location" in {
      val result = repository.getEvents(None, None, Some(EventExamples.LocationNewcastle), List(SkillType.ASSESSOR)).futureValue
      result.size mustBe 1

      result.head.venue mustBe EventExamples.VenueNewcastle
    }

    "filter ALL_EVENTS in LONDON_FSAC" in {
      val result = repository.getEvents(Some(EventType.ALL_EVENTS), Some(EventExamples.VenueLondon)).futureValue
      result.size mustBe 3
      result.exists(_.eventType == EventType.TELEPHONE_INTERVIEW) mustBe true
      result.forall(_.venue == VenueType.LONDON_FSAC)
      result.exists(_.eventType == EventType.FSAC) mustBe true
    }

    "filter FSAC in ALL_VENUES"  in {
      val result = repository.getEvents(Some(EventType.FSAC), Some(EventExamples.VenueAll)).futureValue
      result.size mustBe 3
    }


    "filter and return empty list" in {
      val result = repository.getEvents(Some(EventType.TELEPHONE_INTERVIEW), Some(EventExamples.VenueNewcastle)).futureValue
      result.size mustBe 0
    }

    "return a single event by Id" in {
      val result = repository.getEvent(EventExamples.EventsNew.head.id).futureValue
      result mustBe EventExamples.EventsNew.head
    }

    "return an exception if not event is found by Id" in {
      val result = repository.getEvent("fakeid").failed.futureValue
      result mustBe a[EventNotFoundException]
    }
  }
}
