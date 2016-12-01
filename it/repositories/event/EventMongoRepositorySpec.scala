package repositories.event

import model.persisted.Event
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.EventMongoRepository
import testkit.MongoRepositorySpec

import scala.concurrent.Future

class EventMongoRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = "event"

  lazy val repository = new EventMongoRepository()

  "Event repository" should {
    "insert new event" in {
      val event = Event("ExampleEvent", DateTime.now(DateTimeZone.UTC), Some("appId"), Some("userId"))
      repository.create(event).futureValue
      val result = getEvent(repository.collection.find(BSONDocument.empty).one[BSONDocument])

      result mustBe event
    }
  }

  private def getEvent(doc: Future[Option[BSONDocument]]): Event =
    doc.map(_.map(Event.eventHandler.read)).futureValue.get
}
