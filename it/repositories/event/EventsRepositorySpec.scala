package repositories.events

import factories.UUIDFactory
import model.Exceptions.EventNotFoundException
import repositories.CollectionNames
import model.persisted.eventschedules._
import org.joda.time.{LocalDate, LocalTime}
import testkit.MongoRepositorySpec

class EventsRepositorySpec extends MongoRepositorySpec {

  override def beforeAll(): Unit = {
    super.beforeAll()
    repository.save(events).futureValue
  }

  override def afterAll(): Unit = {
    super.beforeEach()
    super.afterAll()
  }

  override def beforeEach(): Unit = {}

  override val collectionName: String = CollectionNames.ASSESSMENT_EVENTS
  lazy val repository = repositories.eventsRepository
  val venueLondon = Venue("London FSAC", "Bush House")
  val venueNewcastle = Venue("Newcastle FSAC", "Longbenton")

  val london = Location("London")
  val newcastle = Location("Newcastle")
  val events = List(
    Event(id = "1", eventType = EventType.FSAC, location = london,
      venue = venueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSAC, location = london,
      venue = venueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.TELEPHONE_INTERVIEW, location = london,
      venue = venueLondon, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now().plusMinutes(30), endTime = LocalTime.now().plusHours(3),
      skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.SKYPE_INTERVIEW, location = newcastle,
      venue = venueNewcastle, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map()),

    Event(id = UUIDFactory.generateUUID(), eventType = EventType.FSAC, location = newcastle,
      venue = venueNewcastle, date = LocalDate.now(), capacity = 67, minViableAttendees = 60,
      attendeeSafetyMargin = 10, startTime = LocalTime.now(), endTime = LocalTime.now().plusHours(3), skillRequirements = Map())

  )

  "Events" should {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repository)
      indexes must contain theSameElementsAs
        Seq(List("eventType", "date", "location", "venue"), List("_id"))
    }

    "save and fetch events" in {
      val result = repository.fetchEvents(EventType.FSAC, venueLondon).futureValue
      result.size mustBe 2
    }

    "filter FSAC in LONDON_FSAC events" in {
      val result = repository.fetchEvents(EventType.FSAC, venueLondon).futureValue
      result.size mustBe 2
      result.contains(events.head) mustBe true
      result.contains(events.tail.head) mustBe true
    }

    "filter SKYPE_INTERVIEW in NEWCASTLE_LONGBENTON " in {
      val result = repository.fetchEvents(EventType.SKYPE_INTERVIEW, venueNewcastle).futureValue

      result.size mustBe 1
      result.head.venue mustBe venueNewcastle
    }

    "filter ALL_EVENTS in LONDON_FSAC" in {
      val result = repository.fetchEvents(EventType.ALL_EVENTS, venueLondon).futureValue
      result.size mustBe 3
      result.exists(_.eventType == EventType.TELEPHONE_INTERVIEW) mustBe true
      result.forall(_.venue == VenueType.LONDON_FSAC)
      result.exists(_.eventType == EventType.FSAC) mustBe true
    }

    "filter FSAC in ALL_VENUES"  in {
      val result = repository.fetchEvents(EventType.FSAC, Venue("All", "All venues")).futureValue
      result.size mustBe 3
    }

    "filter and return empty list" in {
      val result = repository.fetchEvents(EventType.TELEPHONE_INTERVIEW, venueNewcastle).futureValue

      result.size mustBe 0
    }

    "return a single event by Id" in {
      val result = repository.getEvent(events.head.id).futureValue
      result mustBe events.head
    }

    "return an exception if not event is found by Id" in {
      val result = repository.getEvent("fakeid").failed.futureValue
      result mustBe a[EventNotFoundException]
    }
  }
}
