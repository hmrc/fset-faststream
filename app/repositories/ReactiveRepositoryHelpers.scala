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

import model.Exceptions.{ CannotUpdateRecord, NotFoundException, TooManyEntries }
import play.api.Logger
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{ UpdateWriteResult, WriteResult }
import uk.gov.hmrc.mongo.ReactiveRepository

trait ReactiveRepositoryHelpers {
  this: ReactiveRepository[_, _] =>

  protected lazy val bsonCollection: BSONCollection = collection.db.collection[BSONCollection](collection.name)

  def singleUpdateValidator(id: String,
                            actionDesc: String,
                            notFound: => Exception): UpdateWriteResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNotFound = false, notFound, upsert = false)
  }

  def singleUpdateValidator(id: String, actionDesc: String, ignoreNotFound: Boolean = false): UpdateWriteResult => Unit = {

    singleUpdateValidatorImpl(id, actionDesc, ignoreNotFound,
      new NotFoundException(s"could not find id $id whilst $actionDesc"), upsert = false)
  }

  def singleUpsertValidator(id: String, actionDesc: String): UpdateWriteResult => Unit = {

    singleUpdateValidatorImpl(id, actionDesc, ignoreNotFound = true, new Exception, upsert = true)
  }

  def singleUpsertValidator(id: String, actionDesc: String, notFound: => Exception): UpdateWriteResult => Unit = {

    singleUpdateValidatorImpl(id, actionDesc, ignoreNotFound = true, notFound, upsert = true)
  }


  def multipleRemoveValidator(expected: Int, actionDesc: String): WriteResult => Unit = (result: WriteResult) => {
    if (result.ok) {
      if (result.n == expected) {
        ()
      } else if (result.n == 0) {
        throw new NotFoundException(s"No documents found whilst $actionDesc")
      } else if (result.n > expected) {
        throw TooManyEntries(s"Deletion successful, but too many documents deleted whilst $actionDesc")
      } else if (result.n < expected) {
        Logger.error(s"Not enough documents deleted for $actionDesc")
      }
    } else {
      val mongoError = result.writeConcernError.map(_.errmsg).mkString(",")
      val msg = s"Failed to $actionDesc -> $mongoError"
      Logger.error(msg)
      throw CannotUpdateRecord(msg)
    }
  }

  def singleRemovalValidator(id: String, actionDesc: String): WriteResult => Unit = (result: WriteResult) => {
    if (result.ok) {
      if (result.n == 1) {
        ()
      } else if (result.n == 0) {
        throw new NotFoundException(s"No document found whilst $actionDesc for id $id")
      } else if (result.n > 1) {
        throw TooManyEntries(s"Deletion successful, but too many documents deleted whilst $actionDesc for id $id")
      }
    } else {
      val mongoError = result.writeConcernError.map(_.errmsg).mkString(",")
      val msg = s"Failed to $actionDesc for id: $id -> $mongoError"
      Logger.error(msg)
      throw CannotUpdateRecord(msg)
    }
  }

  private[this] def singleUpdateValidatorImpl(id: String, actionDesc: String, ignoreNotFound: Boolean,
                                              notFound: => Exception, upsert: Boolean)(result: UpdateWriteResult): Unit = {
    if (result.ok) {
      if (result.n == 1) {
        ()
      } else if (result.n == 0 && ignoreNotFound) {
        val msg = s"Failed to find record whilst $actionDesc for id: $id"
        Logger.debug(msg)
      } else if (result.n == 0) {
        throw notFound
      } else if (result.n > 1) {
        throw TooManyEntries(s"Update successful, but too many documents updated whilst $actionDesc for id $id")
      }
    } else {
      val mongoError = result.writeConcernError.map(_.errmsg).mkString(",")
      val msg = s"Failed to $actionDesc for id: $id -> $mongoError"
      Logger.error(msg)
      throw CannotUpdateRecord(msg)
    }
  }
}
