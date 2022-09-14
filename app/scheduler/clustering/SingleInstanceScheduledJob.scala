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

package scheduler.clustering

import java.util.concurrent.{ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit}
import scheduler.scheduling.ExclusiveScheduledJob
import scheduler.{BasicJobConfig, LockKeeper}
import uk.gov.hmrc.mongo.MongoComponent
import java.util.concurrent.{ ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit }

import scheduler.{ BasicJobConfig, LockKeeper }
//import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob //TODO: the hmrc lib also provides one of these investigate if we can use this instead

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait SingleInstanceScheduledJob[C <: BasicJobConfig[_]] extends ExclusiveScheduledJob {
  def config: C

  def lockId: String = config.lockId
  def forceLockReleaseAfter: Duration = config.forceLockReleaseAfter
  def interval = config.interval
  def initialDelay = config.initialDelay
  def configuredInterval = config.configuredInterval
  def name = config.name
  def enabled = config.enabled

  val mongoComponent: MongoComponent

  @volatile
  var running = false

  lazy val lockKeeper: LockKeeper = LockKeeper(mongoComponent, lockId, forceLockReleaseAfter)

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(
    new ThreadPoolExecutor(2, 2, 180, TimeUnit.SECONDS, new ArrayBlockingQueue(4)))

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit]

  /**
    * The lock keeper can take the lock for longer than it is being used to run the task when using
    * greedy locking. This is to enforce tasks that can be should be done no more frequently than the
    * lock duration. Sometimes, such as when shutting down, it is nice to know if the task is
    * running or merely retaining a lock to maintain an interval
    */
  override def isRunning: Future[Boolean] = Future.successful(running)

  override def executeInMutex(implicit ec: ExecutionContext): Future[this.Result] = lockKeeper.tryLock {
    running = true
    val v = Try(tryExecute)
    running = false
    v.get
  }.map {
    case Some(_) => Result("Success")
    case None => Result("Nothing")
  }
}
