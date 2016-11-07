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

package scheduler.clustering

import scheduler.LockKeeper
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait SingleInstanceScheduledJob extends ExclusiveScheduledJob {
  val lockId: String
  val forceLockReleaseAfter: Duration

  @volatile
  var running = false

  lazy val lockKeeper: LockKeeper = LockKeeper(lockIdToUse = lockId, forceLockReleaseAfterToUse = forceLockReleaseAfter)
  implicit val ec: ExecutionContext

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit]

  /**
    * The lock keeper can take the lock for longer than it is being used to run the task when using
    * greedy locking. This is to enforce tasks that can be should be done no more frequently than the
    * lock duration. Sometimes, such as when shutting down, it is nice to know if the task is
    * running or merely retaining a lock to maintain an interval
    */
  override def isRunning: Future[Boolean] = Future.successful(running)

  def executeInMutex(implicit ec: ExecutionContext): Future[this.Result] = lockKeeper.tryLock {
    running = true
    val v = Try(tryExecute)
    running = false
    v.get
  }.map {
    case Some(x) => Result("Done")
    case None => Result("Nothing")
  }
}
