/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import model.persisted.StcEvent
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.CollectionNames
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait StcEventRepository {
  def create(event: StcEvent): Future[Unit]
}

@Singleton
class StcEventMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[StcEvent, BSONObjectID](
    CollectionNames.EVENT,
    mongoComponent.mongoConnector.db,
    StcEvent.eventFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with StcEventRepository {

  override def create(event: StcEvent): Future[Unit] = {
    val doc = StcEvent.eventHandler.write(event)
    collection.insert(ordered = false).one(doc) map (_ => ())
  }
}
