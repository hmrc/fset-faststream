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

package scheduler

import play.api.Logging
import repositories.{LockMongoRepository, LockRepository}
import uk.gov.hmrc.mongo.MongoComponent

import java.time.Duration
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait LockKeeper extends Logging {
  val repo: LockRepository
  val lockId: String
  val serverId: String
  val forceLockReleaseAfter: Duration
  val greedyLockingEnabled: Boolean

  def isLocked(implicit ec: ExecutionContext): Future[Boolean] = repo.isLocked(lockId, serverId)

  def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] = {
    repo.lock(lockId, serverId, forceLockReleaseAfter).flatMap { lockAcquired =>
      if (lockAcquired) {
        body.flatMap { result =>
          if (greedyLockingEnabled) {
            Future.successful(Some(result))
          } else {
            repo.releaseLock(lockId, serverId).map(_ => Some(result))
          }
        }.recoverWith {
          case ex if !greedyLockingEnabled =>
            // Old scala 2 implementation, which doesn't compile in Scala 3:
//          repo.releaseLock(lockId, serverId).flatMap(_ => Future.failed(ex))
            // Changed to this to work in Scala 3:
            logger.error(s"Error occurred whilst trying to acquire lock: ${ex.getMessage}")
            repo.releaseLock(lockId, serverId).flatMap(_ => Future.successful(None))
        }
      } else {
        Future.successful(None)
      }
    }
  }
}

object LockKeeper {

  private lazy val generatedServerId = UUID.randomUUID().toString

  def apply(mongoComponent: MongoComponent,
            lockIdToUse: String,
            forceLockReleaseAfterToUse: scala.concurrent.duration.Duration)(implicit ec: ExecutionContext): LockKeeper = new LockKeeper {
    val forceLockReleaseAfter: Duration = Duration.ofMillis(forceLockReleaseAfterToUse.toMillis)
    val serverId = generatedServerId
    val lockId = lockIdToUse
    val repo = new LockMongoRepository(mongoComponent)
    val greedyLockingEnabled: Boolean = true
  }
}
