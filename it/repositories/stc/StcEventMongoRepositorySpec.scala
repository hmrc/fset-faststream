package repositories.stc

import factories.DateTimeFactoryImpl
import model.persisted.StcEvent
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Inject
import scala.concurrent.Future

class StcEventMongoRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.EVENT

  lazy val repository = new StcEventMongoRepository(mongo)

  val dateTimeFactory = new DateTimeFactoryImpl

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
          case Some(event) => event
          case _ => throw new RuntimeException("No event found")
        }
      }
    }
  }

  val eventTestRepo = new StcEventMongoRepositoryForTest(mongo)

  "Stop the Clock event repository" should {
    "insert new event" in {
      val event = StcEvent("ExampleEvent", dateTimeFactory.nowLocalTimeZone, Some("appId"), Some("userId"))
      repository.create(event).futureValue
      val result = eventTestRepo.getEvent.futureValue
      result mustBe event
    }
  }
}
