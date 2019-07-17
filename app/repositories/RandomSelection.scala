/*
 * Copyright 2019 HM Revenue & Customs
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

import reactivemongo.api.QueryOpts
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{ BSONDocument, BSONDocumentReader }
import reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

object RandomSelection {
  def calculateBatchSize(originalSize: Int, queryCount: Int): (Int, Int) = originalSize match {
    case 1 => (Random.nextInt(queryCount), 1)
    case _ if queryCount <= originalSize => (0, queryCount)
    case _ => (Random.nextInt(queryCount - originalSize), originalSize)
  }
}

trait RandomSelection {
  this: ReactiveRepository[_, _] =>

  protected val bsonCollection: BSONCollection

  // In Mongo 3.2.0 it would be more efficient and safer to use `db.aggregate` with the new `$sample` aggregation
  // to randomly select a single record.
  protected def selectRandom[T](query: BSONDocument, batchSize: Int = 1, projection: BSONDocument = BSONDocument.empty)(
    implicit reader: BSONDocumentReader[T], ec: ExecutionContext): Future[List[T]] = {

    collection.runCommand(JSONCountCommand.Count(query)).flatMap { c =>
      val count = c.count
      if (count == 0) {
        Future.successful(Nil)
      } else {
        // In the event of a race-condition where the size decreases after counting, the worse-case is that
        // `None` is returned by the method instead of a Some[BSONDocument].
        val (randomIndex, newBatchSize) = RandomSelection.calculateBatchSize(batchSize, count)

        bsonCollection.find(query, projection)
          .options(QueryOpts(skipN = randomIndex, batchSizeN = newBatchSize))
          .cursor[T]().collect[List](newBatchSize)
      }
    }
  }

  protected def selectOneRandom[T](query: BSONDocument)(
    implicit reader: BSONDocumentReader[T], ec: ExecutionContext): Future[Option[T]] = {
    selectRandom[T](query, 1).map(_.headOption)
  }
}
