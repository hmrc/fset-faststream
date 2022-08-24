/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.{ExecutionContext, Future}

//TODO: mongo the upsert setting does nothing!!!!
//TODO: mongo this needs to be renamed because ReactiveRepository has become PlayMongoRepository
trait ReactiveRepositoryHelpers extends Logging {
  this: PlayMongoRepository[_] =>

  def singleUpdateValidator(id: String,
                            actionDesc: String,
                            error: => Exception): UpdateResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNoRecordUpdated = false, error, upsert = false)
  }

  def singleUpdateValidator(id: String,
                            actionDesc: String,
                            ignoreNoRecordUpdated: Boolean,
                            error: => Exception): UpdateResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNoRecordUpdated, error, upsert = false)
  }

  def singleUpdateValidator(id: String, actionDesc: String, ignoreNoRecordUpdated: Boolean = false): UpdateResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNoRecordUpdated,
      CannotUpdateRecord(s"Failed to update document for Id $id whilst performing operation $actionDesc"), upsert = false
    )
  }

  def singleUpsertValidator(id: String, actionDesc: String): UpdateResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNoRecordUpdated = true, new Exception, upsert = true)
  }

  def singleUpsertValidator(id: String, actionDesc: String, error: => Exception): UpdateResult => Unit = {
    singleUpdateValidatorImpl(id, actionDesc, ignoreNoRecordUpdated = true, error, upsert = true)
  }

  private def handleUnacknowledgedResult(errorPrefix: String) = {
    // ReactiveMongo driver gave us access to the error via this:
    //  val mongoError = result.writeConcernError.map(_.errmsg).mkString(",")
    val msg = s"$errorPrefix -> Mongo failed to acknowledge result"
    logger.error(msg)
    throw CannotUpdateRecord(msg)
  }

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
      handleUnacknowledgedResult(s"Failed to $actionDesc")
    }
  }

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
      handleUnacknowledgedResult(s"Failed to $actionDesc for id: $id")
    }
  }

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
      handleUnacknowledgedResult(s"Failed to $actionDesc for id: $id")
    }
  }

  def countLong(implicit ec:ExecutionContext): Future[Long] = {
    collection.countDocuments().toFuture()
  }
}
