package repositories.event

import model.Exceptions.EventNotFoundException
import repositories.CollectionNames
import model.persisted.EventExamples
import model.persisted.eventschedules.{ EventType, SkillType, VenueType }
import org.joda.time.DateTime
import testkit.MongoRepositorySpec

import scala.util.Random

class EventsRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.ASSESSMENT_EVENTS

  lazy val repository = repositories.eventsRepository

  "Events Repository" should {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repository)
      indexes must contain theSameElementsAs
        Seq(List("eventType", "date", "location", "venue"), List("_id"))
    }
  }

  "save" should {
    "save and fetch events" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEvents(Some(EventType.FSAC), Some(EventExamples.VenueLondon)).futureValue
      result.size mustBe 2
    }
  }

  "updateEvent" should {
    "update events" in {
      val eventId = "eventId"
      val event = EventExamples.e1WithSessions.copy(id = eventId, skillRequirements = Map(SkillType.ASSESSOR.toString -> 1))
      repository.save(event :: Nil).futureValue mustBe unit

      val updatedSessions = event.sessions.head.copy(capacity = 40, minViableAttendees = 30, attendeeSafetyMargin = 10) :: event.sessions.tail
      val updatedEvent = event.copy(skillRequirements = Map(SkillType.ASSESSOR.toString -> 4), sessions = updatedSessions)
      repository.updateEvent(updatedEvent).futureValue mustBe unit
      repository.getEvent(eventId).futureValue mustBe updatedEvent
    }
  }

  "findAll" should {
    "find all events" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.findAll().futureValue
      result.size mustBe 5
      result must contain theSameElementsAs EventExamples.EventsNew
    }
  }

  "getEvents" should {
    "filter FSAC in LONDON_FSAC events" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEvents(Some(EventType.FSAC), Some(EventExamples.VenueLondon)).futureValue
      result.size mustBe 2
      result.contains(EventExamples.EventsNew.head) mustBe true
      result.contains(EventExamples.EventsNew.tail.head) mustBe true
    }

    "filter FSB in NEWCASTLE_LONGBENTON " in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEvents(Some(EventType.FSB), Some(EventExamples.VenueNewcastle)).futureValue
      result.size mustBe 2
      result.head.venue mustBe EventExamples.VenueNewcastle
    }

    "filter by skills" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEvents(None, None, None, List(SkillType.QUALITY_ASSURANCE_COORDINATOR)).futureValue

      result.size mustBe 1
      result.head.venue mustBe EventExamples.VenueNewcastle

    }

    "filter by skills and Location" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEvents(None, None, Some(EventExamples.LocationNewcastle), List(SkillType.ASSESSOR)).futureValue
      result.size mustBe 1

      result.head.venue mustBe EventExamples.VenueNewcastle
    }

    "filter ALL_EVENTS in LONDON_FSAC" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEvents(Some(EventType.ALL_EVENTS), Some(EventExamples.VenueLondon)).futureValue
      result.size mustBe 3
      result.exists(_.eventType == EventType.FSB) mustBe true
      result.forall(_.venue == VenueType.LONDON_FSAC)
      result.exists(_.eventType == EventType.FSAC) mustBe true
    }

    "filter FSAC in ALL_VENUES" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEvents(Some(EventType.FSAC), Some(EventExamples.VenueAll)).futureValue
      result.size mustBe 2
    }

    "filter FSB subtype in ALL_VENUES" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEvents(Some(EventType.FSB), Some(EventExamples.VenueAll), description = Some("EAC")).futureValue
      result.size mustBe 1
    }

    "filter and return empty list" in {
      repository.save(EventExamples.EventsNew.filter(_.eventType == EventType.FSAC)).futureValue
      val result = repository.getEvents(Some(EventType.FSB), Some(EventExamples.VenueNewcastle)).futureValue
      result.size mustBe 0
    }
  }

  "getEvent" should {
    "return a single event by Id" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEvent(EventExamples.EventsNew.head.id).futureValue
      result mustBe EventExamples.EventsNew.head
    }

    "return an exception if not event is found by Id" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEvent("fakeid").failed.futureValue
      result mustBe a[EventNotFoundException]
    }
  }

  "getEventsById" should {
    "return multiple events by Id" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEventsById(EventExamples.EventsNew.map(_.id)).futureValue
      result mustBe EventExamples.EventsNew
    }

    "return multiple events by Id with filter on event type" in {
      repository.save(EventExamples.EventsNew).futureValue
      val result = repository.getEventsById(EventExamples.EventsNew.map(_.id), Some(EventType.FSB)).futureValue
      result must contain theSameElementsAs EventExamples.EventsNew.filter(_.eventType == EventType.FSB)
    }

    "return the newly created events since the specified date" in {
      val hoursInPast = 24
      val createdAt = DateTime.now().minusDays(hoursInPast)
      val events = EventExamples.EventsNew.map(_.copy(createdAt = createdAt))
      repository.save(events).futureValue mustBe unit

      // this helps us create events at random hours rather using just one
      def randomHour = 1 + new Random().nextInt( (hoursInPast - 1) + 1 )

      val bulkInsertedEvent = EventExamples.e1WithSession.copy(createdAt = createdAt.plusHours(randomHour), wasBulkUploaded = true)
      repository.save(List(bulkInsertedEvent)).futureValue mustBe unit

      val newEvents = EventExamples.EventsNew.map(_.copy(createdAt = createdAt.plusHours(randomHour)))
      repository.save(newEvents).futureValue mustBe unit

      val result = repository.getEventsManuallyCreatedAfter(createdAt.plusMinutes(1)).futureValue
      result mustBe newEvents
    }
  }
}
