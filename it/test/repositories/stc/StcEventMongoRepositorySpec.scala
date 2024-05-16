/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package repositories.stc

import model.persisted.StcEvent
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

import java.time.temporal.ChronoUnit
import java.time.{OffsetDateTime, ZoneId}

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
      val event = StcEvent("ExampleEvent",
        OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS),
        Some("appId"), Some("userId")
      )
      repository.create(event).futureValue
      val result = getEvent
      result mustBe event
    }
  }
}
