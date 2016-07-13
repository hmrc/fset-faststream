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

import scheduled.LockKeeper
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

trait SingleInstanceScheduledJob extends ExclusiveScheduledJob {
  val lockId: String
  val forceLockReleaseAfter: Duration

  lazy val lockKeeper: LockKeeper = LockKeeper(lockIdToUse = lockId, forceLockReleaseAfterToUse = forceLockReleaseAfter)
  implicit val ec: ExecutionContext

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit]

  override def isRunning: Future[Boolean] = for {
    lockedOnMongo <- lockKeeper.isLocked
    lockedOnJvm <- super.isRunning
  } yield {
    lockedOnMongo || lockedOnJvm
  }

  def executeInMutex(implicit ec: ExecutionContext): Future[this.Result] = lockKeeper.tryLock {
    tryExecute
  }.map {
    case Some(x) => Result("Done")
    case None => Result("Nothing")
  }

}
