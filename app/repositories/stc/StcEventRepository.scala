/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import repositories.CollectionNames
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait StcEventRepository {
  def create(event: StcEvent): Future[Unit]
}

@Singleton
class StcEventMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[StcEvent](
    collectionName = CollectionNames.EVENT,
    mongoComponent = mongo,
    domainFormat = StcEvent.eventFormat,
    indexes = Nil
  ) with StcEventRepository {

  override def create(event: StcEvent): Future[Unit] = {
    collection.insertOne(event).toFuture() map (_ => ())
  }
}
