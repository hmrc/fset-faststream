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

package repositories

import model.Exceptions.{CannotUpdateRecord, NotFoundException, TooManyEntries}
import org.mongodb.scala.result.{DeleteResult, UpdateResult}
import play.api.Logging
//import play.api.libs.json.JsObject
//import reactivemongo.api.{ ReadConcern, ReadPreference, WriteConcern }
//import reactivemongo.api.collections.bson.BSONBatchCommands.FindAndModifyCommand
//import reactivemongo.api.collections.bson.BSONCollection
//import reactivemongo.api.commands.{ Collation, UpdateWriteResult, WriteResult }
//import reactivemongo.bson.BSONDocument
//import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

//TODO: mongo the upsert setting does nothing!!!!
trait ReactiveRepositoryHelpers extends Logging {
//  this: ReactiveRepository[_, _] =>

//  protected lazy val bsonCollection: BSONCollection = collection.db.collection[BSONCollection](collection.name)

/*
  def singleUpdateValidator(id: String,
                            actionDesc: String,
                            notFound: => Exception): UpdateWriteResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNotFound = false, notFound, upsert = false)
  }*/

  def singleUpdateValidator(id: String,
                            actionDesc: String,
                            error: => Exception): UpdateResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNoRecordUpdated = false, error, upsert = false)
  }

/*
  def singleUpdateValidator(id: String, actionDesc: String, ignoreNotFound: Boolean = false): UpdateWriteResult => Unit = {

    singleUpdateValidatorImpl(id, actionDesc, ignoreNotFound,
      new NotFoundException(s"could not find id $id whilst $actionDesc"), upsert = false)
  }*/

  def singleUpdateValidator(id: String, actionDesc: String, ignoreNotFound: Boolean = false): UpdateResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNotFound,
      new CannotUpdateRecord(s"Failed to update document for applicationId $id whilst performing operation $actionDesc"), upsert = false
    )
  }

/*
  def singleUpsertValidator(id: String, actionDesc: String): UpdateWriteResult => Unit = {

    singleUpdateValidatorImpl(id, actionDesc, ignoreNotFound = true, new Exception, upsert = true)
  }*/

  def singleUpsertValidator(id: String, actionDesc: String): UpdateResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNoRecordUpdated = true, new Exception, upsert = true)
  }

/*
  def singleUpsertValidator(id: String, actionDesc: String, notFound: => Exception): UpdateWriteResult => Unit = {

    singleUpdateValidatorImpl(id, actionDesc, ignoreNotFound = true, notFound, upsert = true)
  }*/

  def singleUpsertValidator(id: String, actionDesc: String, error: => Exception): UpdateResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNoRecordUpdated = true, error, upsert = true)
  }

/*
  def multipleRemoveValidator(expected: Int, actionDesc: String): WriteResult => Unit = (result: WriteResult) => {
    if (result.ok) {
      if (result.n == expected) {
        ()
      } else if (result.n == 0) {
        throw new NotFoundException(s"No documents found whilst $actionDesc")
      } else if (result.n > expected) {
        throw TooManyEntries(s"Deletion successful, but too many documents deleted whilst $actionDesc")
      } else if (result.n < expected) {
        logger.error(s"Not enough documents deleted for $actionDesc")
      }
    } else {
      val mongoError = result.writeConcernError.map(_.errmsg).mkString(",")
      val msg = s"Failed to $actionDesc -> $mongoError"
      logger.error(msg)
      throw CannotUpdateRecord(msg)
    }
  }*/

  def multipleRemoveValidator(expected: Int, actionDesc: String): DeleteResult => Unit = (result: DeleteResult) => {
    if (result.wasAcknowledged()) {
      if (result.getDeletedCount == expected) {
        ()
      } else if (result.getDeletedCount == 0) {
        throw new NotFoundException(s"No documents found whilst $actionDesc")
      } else if (result.getDeletedCount > expected) {
        throw TooManyEntries(s"Deletion successful, but too many documents deleted whilst $actionDesc")
      } else if (result.getDeletedCount < expected) {
        logger.error(s"Not enough documents deleted for $actionDesc")
      }
    } else {
      // TODO: mongo fix
//      val mongoError = result.writeConcernError.map(_.errmsg).mkString(",")
      val mongoError = "FIX ME"
      val msg = s"Failed to $actionDesc -> $mongoError"
      logger.error(msg)
      throw CannotUpdateRecord(msg)
    }
  }

/*
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
      logger.error(msg)
      throw CannotUpdateRecord(msg)
    }
  }*/

  def singleRemovalValidator(id: String, actionDesc: String): DeleteResult => Unit = (result: DeleteResult) => {
    if (result.wasAcknowledged()) {
      if (result.getDeletedCount == 1) {
        ()
      } else if (result.getDeletedCount == 0) {
        throw new NotFoundException(s"No document found whilst $actionDesc for id $id")
      } else if (result.getDeletedCount > 1) {
        throw TooManyEntries(s"Deletion successful, but too many documents deleted whilst $actionDesc for id $id")
      }
    } else {
      //TODO: mongo fix
//      val mongoError = result.writeConcernError.map(_.errmsg).mkString(",")
      val mongoError = "FIX ME"
      val msg = s"Failed to $actionDesc for appId: $id -> $mongoError"
      logger.error(msg)
      throw CannotUpdateRecord(msg)
    }
  }

/*
  private[this] def singleUpdateValidatorImpl(id: String, actionDesc: String, ignoreNotFound: Boolean,
                                              notFound: => Exception, upsert: Boolean)(result: UpdateWriteResult): Unit = {
    if (result.ok) {
      if (result.n == 1) {
        ()
      } else if (result.n == 0 && ignoreNotFound) {
        val msg = s"Failed to find record whilst $actionDesc for id: $id"
        logger.debug(msg)
      } else if (result.n == 0) {
        throw notFound
      } else if (result.n > 1) {
        throw TooManyEntries(s"Update successful, but too many documents updated whilst $actionDesc for id $id")
      }
    } else {
      val mongoError = result.writeConcernError.map(_.errmsg).mkString(",")
      val msg = s"Failed to $actionDesc for id: $id -> $mongoError"
      logger.error(msg)
      throw CannotUpdateRecord(msg)
    }
  }*/
  private[this] def singleUpdateValidatorImpl(id: String,
                                              actionDesc: String,
                                              ignoreNoRecordUpdated: Boolean,
                                              error: => Exception,
                                              upsert: Boolean)(result: UpdateResult): Unit = {
    if (result.wasAcknowledged()) {
      if (result.getModifiedCount == 1) {
        logger.debug(s"Successfully updated ${result.getModifiedCount} document(s) whilst $actionDesc for id $id")
        ()
      } else if (result.getModifiedCount == 0 && ignoreNoRecordUpdated) {
        val msg = s"Failed to update record whilst $actionDesc for id: $id. IgnoreNoRecordUpdated is on for this operation"
        logger.warn(msg)
      } else if (result.getModifiedCount == 0) {
        throw error
      } else if (result.getModifiedCount > 1) {
        throw TooManyEntries(s"Update successful, but too many documents updated whilst $actionDesc for id $id")
      }
    } else {
      //TODO: mongo fix
//      val mongoError = result.writeConcernError.map(_.errmsg).mkString(",")
      val mongoError = "FIX ME"
      val msg = s"Failed to $actionDesc for appId: $id -> $mongoError"
      logger.error(msg)
      throw CannotUpdateRecord(msg)
    }
  }//scalastyle:on

/*
  // Wrap the findAndModify method to provide all the defaults
  def findAndModify(query: BSONDocument, updateOp: FindAndModifyCommand.Update) = {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    bsonCollection.findAndModify(
      query, updateOp, sort = None, fields = None, bypassDocumentValidation = false,
      writeConcern = WriteConcern.Default, maxTime = Option.empty[FiniteDuration], collation = Option.empty[Collation],
      arrayFilters = Seq.empty[BSONDocument]
    )
  }*/

/*
  // Alternative to the count implemented by Hmrc ReactiveRepository class, which throws a JsResultException at runtime:
  // errmsg=readConcern.level must be either 'local', 'majority' or 'linearizable'", "")
  def countLong(implicit ec: ExecutionContext): Future[Long] =
    collection.withReadPreference(ReadPreference.primary).count(
      selector = Option.empty[JsObject],
      limit = None,
      skip = 0,
      hint =  None,
      readConcern = ReadConcern.Local
    )*/
}
