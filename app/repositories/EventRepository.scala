/*
 * Copyright 2017 HM Revenue & Customs
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

package repositories

import model.persisted.Event
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait EventRepository {

  def create(event: Event): Future[Unit]
}

class EventMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Event, BSONObjectID](CollectionNames.EVENT, mongo, Event.eventFormat,
    ReactiveMongoFormats.objectIdFormats) with EventRepository {

  def create(event: Event): Future[Unit] = {
    val doc = Event.eventHandler.write(event)
    collection.insert[BSONDocument](doc) map (_ => ())
  }
}
