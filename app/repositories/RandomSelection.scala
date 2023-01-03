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

import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Aggregates.{`match`, sample}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait RandomSelection {
  // Enforce that classes that extend this trait provide a PlayMongoRepository impl so we can access the collection
  this: PlayMongoRepository[_] =>

  protected def selectRandom[T](query: Document, batchSize: Int = 1)(implicit reader: Document => T, ec: ExecutionContext): Future[Seq[T]] = {
    collection.aggregate[Document](Seq(
      `match`(query),
      sample(batchSize) // This is the number of random documents returned
    )).toFuture().map ( _.map ( reader ))
  }

  protected def selectOneRandom[T](query: Document)(implicit reader: Document => T, ec: ExecutionContext): Future[Option[T]] = {
    // Explicitly pass the implicits to avoid ambiguous implicit values compile error
    selectRandom(query)(reader, ec).map( _.headOption )
  }

  // Use this selectRandom when you are using a MongoCollection[T] and the codec that is automatically registered to read data back from Mongo.
  // This can either be the default one that is created when you set up your MongoRepository or alternate one setup to work with a specific
  // domain format
  protected def selectRandom[T: ClassTag](collection: MongoCollection[T], query: Document, batchSize: Int): Future[Seq[T]] = {
    collection.aggregate(Seq(
      `match`(query),
      sample(batchSize) // This is the number of random documents returned
    )).toFuture()
  }
}
