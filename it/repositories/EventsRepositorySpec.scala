package repositories

import model.persisted.EventExamples
import model.persisted.eventschedules.{ EventType, VenueType }
import testkit.MongoRepositorySpec

class EventsRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.ASSESSMENT_EVENTS
  lazy val repository = repositories.eventsRepository
  val events = EventExamples.EventsNew

  "Assessment Events" should {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repository)
      indexes must contain theSameElementsAs
        Seq(List("eventType", "date", "location", "venue"), List("_id"))
    }

    "save and fetch events" in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(Some(EventType.FSAC), Some(VenueType.LONDON_FSAC)).futureValue
      result.size mustBe 2
    }

    "filter FSAC in LONDON_FSAC events" in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(Some(EventType.FSAC), Some(VenueType.LONDON_FSAC)).futureValue
      result.size mustBe 2
      result.contains(events.head) mustBe true
      result.contains(events.tail.head) mustBe true
    }

    "filter SKYPE_INTERVIEW in NEWCASTLE_LONGBENTON " in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(Some(EventType.SKYPE_INTERVIEW), Some(VenueType.NEWCASTLE_LONGBENTON)).futureValue

      result.size mustBe 1
      result.head.venue mustBe VenueType.NEWCASTLE_LONGBENTON.toString
    }

    "filter by skills" in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(None, None, None, Some(List("QAC"))).futureValue

      result.size mustBe 1
      result.head.venue mustBe VenueType.LONDON_FSAC.toString

    }

    "filter by skills and Location" in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(None, None, Some("Newcastle"), Some(List("ASSESSOR"))).futureValue

      result.size mustBe 1
      result.head.venue mustBe VenueType.NEWCASTLE_LONGBENTON.toString

    }


    "filter and return empty list" in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(Some(EventType.FSAC), Some(VenueType.NEWCASTLE_FSAC)).futureValue

      result.size mustBe 0
    }
  }
}
