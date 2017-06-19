package repositories.event

import model.persisted.AuditEvent
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.{ CollectionNames, AuditEventMongoRepository }
import testkit.MongoRepositorySpec

import scala.concurrent.Future

class EventMongoRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = CollectionNames.EVENT

  lazy val repository = new AuditEventMongoRepository()

  "Event repository" should {
    "insert new event" in {
      val event = AuditEvent("ExampleEvent", DateTime.now(DateTimeZone.UTC), Some("appId"), Some("userId"))
      repository.create(event).futureValue
      val result = getEvent(repository.collection.find(BSONDocument.empty).one[BSONDocument])

      result mustBe event
    }
  }

  private def getEvent(doc: Future[Option[BSONDocument]]): AuditEvent =
    doc.map(_.map(AuditEvent.eventHandler.read)).futureValue.get
}
