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

package repositories

import model.persisted.AuditEvent
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AuditEventRepository {
  def create(event: AuditEvent): Future[Unit]
}

@Singleton
class AuditEventMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[AuditEvent](
    collectionName = CollectionNames.EVENT,
    mongoComponent = mongo,
    domainFormat = AuditEvent.auditEventFormat,
    indexes = Nil
  ) with AuditEventRepository {

  def create(event: AuditEvent): Future[Unit] =
    collection.insertOne(event).toFuture() map (_ => ())
}
