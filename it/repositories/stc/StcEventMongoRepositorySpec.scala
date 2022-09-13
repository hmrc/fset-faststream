package repositories.stc

import model.persisted.StcEvent
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

class StcEventMongoRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.EVENT

  lazy val repository = new StcEventMongoRepository(mongo)

  def getEvent = {
    val eventOpt = repository.collection
      .find[BsonDocument](Document.empty).headOption().map( _.map( Codecs.fromBson[StcEvent]) ).futureValue
    eventOpt match {
      case Some(event) => event
      case _ => throw new RuntimeException("No event found")
    }
  }

  "Stop the Clock event repository" should {
    "insert new event" in {
      val event = StcEvent("ExampleEvent", DateTime.now(DateTimeZone.UTC), Some("appId"), Some("userId"))
      repository.create(event).futureValue
      val result = getEvent
      result mustBe event
    }
  }
}
