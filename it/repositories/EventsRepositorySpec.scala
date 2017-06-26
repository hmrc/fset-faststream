package repositories

import model.persisted.EventExamples
import model.persisted.eventschedules.{ EventType, VenueType }
import testkit.MongoRepositorySpec

class EventsRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.ASSESSMENT_EVENTS
  lazy val repository = repositories.eventsRepository

  "Events" should {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repository)
      indexes must contain theSameElementsAs
        Seq(List("eventType", "date", "location", "venue"), List("_id"))
    }

    "save and fetch events" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.fetchEvents(Some(EventType.FSAC), Some(VenueType.LONDON_FSAC), None, None).futureValue
      result.size mustBe 2
    }

    "filter FSAC in LONDON_FSAC events" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.fetchEvents(Some(EventType.FSAC), Some(VenueType.LONDON_FSAC), None, None).futureValue
      result.size mustBe 2
      result.contains(EventExamples.EventsNew.head) mustBe true
      result.contains(EventExamples.EventsNew.tail.head) mustBe true
    }

    "filter SKYPE_INTERVIEW in NEWCASTLE_LONGBENTON " in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.fetchEvents(Some(EventType.SKYPE_INTERVIEW), Some(VenueType.NEWCASTLE_LONGBENTON), None, None).futureValue

      result.size mustBe 1
      result.head.venue mustBe VenueType.NEWCASTLE_LONGBENTON
    }

    "filter by skills" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.fetchEvents(None, None, None, Some(List("QAC"))).futureValue

      result.size mustBe 1
      result.head.venue mustBe VenueType.LONDON_FSAC

    }

    "filter by skills and Location" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.fetchEvents(None, None, Some("Newcastle"), Some(List("ASSESSOR"))).futureValue

      result.size mustBe 1

      result.head.venue mustBe VenueType.NEWCASTLE_LONGBENTON
    }

    "filter ALL_EVENTS in LONDON_FSAC" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.fetchEvents(Some(EventType.ALL_EVENTS), Some(VenueType.LONDON_FSAC), None, None).futureValue
      result.size mustBe 3
      result.exists(_.eventType == EventType.TELEPHONE_INTERVIEW) mustBe true
      result.forall(_.venue == VenueType.LONDON_FSAC)
      result.exists(_.eventType == EventType.FSAC) mustBe true
    }

    "filter FSAC in ALL_VENUES"  in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.fetchEvents(Some(EventType.FSAC), Some(VenueType.ALL_VENUES), None, None).futureValue
      result.size mustBe 3
    }


    "filter and return empty list" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.fetchEvents(Some(EventType.FSAC), Some(VenueType.NEWCASTLE_FSAC), None, None).futureValue

      result.size mustBe 0
    }
  }
}
