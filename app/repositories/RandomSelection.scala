/*
 * Copyright 2016 HM Revenue & Customs
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
import reactivemongo.bson.BSONDocument
import reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

trait RandomSelection {
  this: ReactiveRepository[_, _] =>

  // In Mongo 3.2.0 it would be more efficient and safer to use `db.aggregate` with the new `$sample` aggregation
  // to randomly select a single record.
  def selectRandom(query: BSONDocument, batchSize: Int = 1)(implicit ec: ExecutionContext): Future[Option[BSONDocument]] =
    collection.runCommand(JSONCountCommand.Count(query)).flatMap { c =>
      val count = c.count
      if (count == 0) {
        Future.successful(None)
      } else {
        // In the event of a race-condition where the size decreases after counting, the worse-case is that
        // `None` is returned by the method instead of a Some[BSONDocument].
        val (randomIndex, newBatchSize) = batchSize match {
          case 1 => (Random.nextInt(count), 1)
          case _ => if (count < batchSize) { (0, count) }
            else { (Random.nextInt(batchSize - count), batchSize) }
        }

        collection.find(query)
          .options(QueryOpts(skipN = randomIndex, batchSizeN = newBatchSize))
          .cursor[BSONDocument]().collect[List](1)
          .map(_.headOption)
      }
    }
}
