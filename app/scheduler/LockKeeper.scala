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

package scheduler

import java.util.UUID
import org.joda.time.Duration
import uk.gov.hmrc.mongo.MongoComponent
import repositories._

import scala.concurrent.{ ExecutionContext, Future }

trait LockKeeper {
  val repo: LockRepository
  val lockId: String
  val serverId: String
  val forceLockReleaseAfter: Duration
  val greedyLockingEnabled: Boolean

  def isLocked(implicit ec: ExecutionContext): Future[Boolean] = repo.isLocked(lockId, serverId)

  def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] = {
    repo.lock(lockId, serverId, forceLockReleaseAfter)
      .flatMap { acquired =>
        if (acquired) {
          body.flatMap { x =>
              if (greedyLockingEnabled) {
                Future.successful(Some(x))
              } else {
                repo.releaseLock(lockId, serverId).map(_ => Some(x))
              }
          }.recoverWith { case ex if !greedyLockingEnabled => repo.releaseLock(lockId, serverId).flatMap(_ => Future.failed(ex)) }
        } else {
          Future.successful(None)
        }
      }
  }
}

object LockKeeper {

  lazy val generatedServerId = UUID.randomUUID().toString

  def apply(mongoComponent: MongoComponent,
            lockIdToUse: String,
            forceLockReleaseAfterToUse: scala.concurrent.duration.Duration) = new LockKeeper {
    val forceLockReleaseAfter: Duration = Duration.millis(forceLockReleaseAfterToUse.toMillis)
    val serverId = generatedServerId
    val lockId = lockIdToUse
    val repo = new LockMongoRepository(mongoComponent)
    val greedyLockingEnabled: Boolean = true
  }
}
