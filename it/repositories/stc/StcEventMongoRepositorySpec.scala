package repositories.stc

import model.persisted.StcEvent
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.JsObject
import repositories.CollectionNames
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Inject
import scala.concurrent.Future

//TODO: mongo fix the tests
class StcEventMongoRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.EVENT

  lazy val repository = new StcEventMongoRepository(mongo)

  // Just here so we can read back the event data
  class StcEventMongoRepositoryForTest @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[StcEvent](
    collectionName = CollectionNames.EVENT,
    mongoComponent = mongo,
    domainFormat = StcEvent.eventFormat,
    indexes = Nil
  ) {
    def getEvent: Future[StcEvent] = {
      for {
        eventOpt <- collection.find(Document.empty).headOption()
      } yield {
        eventOpt match {
          case Some(event) =>
            //scalastyle:off
            println(s"**** read data from db: $event")
            //scalastyle:on
            event
          case _ => throw new RuntimeException("No event found")
        }
      }
    }
  }

  val eventTestRepo = new StcEventMongoRepositoryForTest(mongo)

  "Stop the Clock Event repository" should {
    "insert new event" in {
      val event = StcEvent("ExampleEvent", DateTime.now(DateTimeZone.UTC), Some("appId"), Some("userId"))
      //scalastyle:off
      println(s"**** storing event: $event")
      //scalastyle:on

      repository.create(event).futureValue
      val result = eventTestRepo.getEvent.futureValue
      result mustBe event
    }
  }
}
