package repositories

import factories.UUIDFactory
import model.persisted.eventschedules.{ Event, EventType, VenueType }
import org.joda.time.{ LocalDate, LocalTime }
import testkit.MongoRepositorySpec

class EventsRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.ASSESSMENT_EVENTS
  lazy val repository = repositories.eventsRepository
  val events = List(
    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FAST_STREAM_ASSESSMENT_CENTRE, location = "London",
      venue = VenueType.LONDON_FSAC.toString, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FAST_STREAM_ASSESSMENT_CENTRE, location = "London",
      venue = VenueType.LONDON_FSAC.toString, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.EDIP_TELEPHONE_INTERVIEW, location = "London",
      venue = VenueType.LONDON_FSAC.toString, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.SKYPE_INTERVIEW, location = "Newcastle",
      venue = VenueType.NEWCASTLE_LONGBENTON.toString, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FAST_STREAM_ASSESSMENT_CENTRE, location = "Newcastle",
      venue = VenueType.NEWCASTLE_LONGBENTON.toString, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map())

  )

  "Events" should {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repository)
      indexes must contain theSameElementsAs
        Seq(List("eventType", "date", "location", "venue"), List("_id"))
    }

    "save and fetch events" in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(EventType.FAST_STREAM_ASSESSMENT_CENTRE, VenueType.LONDON_FSAC).futureValue
      result.size mustBe 2
    }

    "filter FSAC in LONDON_FSAC events" in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(EventType.FAST_STREAM_ASSESSMENT_CENTRE, VenueType.LONDON_FSAC).futureValue
      result.size mustBe 2
      result.contains(events.head) mustBe true
      result.contains(events.tail.head) mustBe true
    }

    "filter SKYPE_INTERVIEW in NEWCASTLE_LONGBENTON " in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(EventType.SKYPE_INTERVIEW, VenueType.NEWCASTLE_LONGBENTON).futureValue

      result.size mustBe 1
      result.head.venue mustBe VenueType.NEWCASTLE_LONGBENTON.toString
    }

    "filter ALL_EVENTS in LONDON_FSAC" in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(EventType.ALL_EVENTS, VenueType.LONDON_FSAC).futureValue
      result.size mustBe 3
    }

    "filter FSAC in NEWCASTLE_LONGBENTON"  in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(EventType.FAST_STREAM_ASSESSMENT_CENTRE, VenueType.ALL_VENUES).futureValue
      result.size mustBe 3
    }

    "filter and return empty list" in {
      repository.save(events).futureValue
      val result = repository.fetchEvents(EventType.FAST_STREAM_ASSESSMENT_CENTRE, VenueType.NEWCASTLE_FSAC).futureValue

      result.size mustBe 0
    }
  }
}
