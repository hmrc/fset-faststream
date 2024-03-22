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

import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import repositories.LockRepository
import testkit.UnitSpec

import java.time.Duration
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class LockKeeperSpec extends UnitSpec {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val lockRepositoryMock = mock[LockRepository]

  class TestableLockKeeper extends LockKeeper {
    val repo: LockRepository = lockRepositoryMock
    val lockId = "lockId"
    val serverId = "serverId"
    val forceLockReleaseAfter = Duration.ofMillis(1000)
    val greedyLockingEnabled = true
  }

  val lockKeeper = new TestableLockKeeper
  val timeout = 5 seconds

  val workException = new Exception("failed")
  val workResult = "success"
  def successfulWorkMethod()(implicit ec: ExecutionContext): Future[String] = Future.successful(workResult)
  def failedWorkMethod()(implicit ec: ExecutionContext): Future[String] = Future.failed(workException)

  "lockKeeper is locked" must {
    "return true if a lock exists in the repo" in {
      when(lockRepositoryMock.isLocked("lockId", "serverId")).thenReturn(Future.successful(true))
      lockKeeper.isLocked.futureValue mustBe true
    }

    "return false if a lock does not exist in the repo" in {
      when(lockRepositoryMock.isLocked("lockId", "serverId")).thenReturn(Future.successful(false))
      lockKeeper.isLocked.futureValue mustBe false
    }
  }

  "Greedy lockKeeper lock" must {
    "not do work work if the lock is not taken" in {
      when(lockRepositoryMock.lock(eqTo("lockId"), eqTo("serverId"), any[Duration]))
        .thenReturn(Future.successful(false))

      lockKeeper.tryLock(successfulWorkMethod)(ec).futureValue mustBe None
    }

    "do work if the lock can be taken and not release the lock" in {
      when(lockRepositoryMock.lock(eqTo("lockId"), eqTo("serverId"), any[Duration]))
        .thenReturn(Future.successful(true))

      lockKeeper.tryLock(successfulWorkMethod)(ec).futureValue mustBe Some(workResult)
      verify(lockRepositoryMock, times(0)).releaseLock("lockId", "serverId")
    }

    "fail when the lock method throws an exception" in {
      val lockAquiringException = new RuntimeException("test exception")
      when(lockRepositoryMock.lock(eqTo("lockId"), eqTo("serverId"), any[Duration]))
        .thenReturn(Future.failed(lockAquiringException))

      lockKeeper.tryLock(successfulWorkMethod)(ec).failed.futureValue mustBe lockAquiringException

      verify(lockRepositoryMock, times(0)).releaseLock("lockId", "serverId")
    }

    "not release the lock when the work method throws an exception" in {
      when(lockRepositoryMock.lock(eqTo("lockId"), eqTo("serverId"), any[Duration]))
        .thenReturn(Future.successful(true))

      lockKeeper.tryLock(failedWorkMethod)(ec).failed.futureValue mustBe workException

      verify(lockRepositoryMock, times(0)).releaseLock("lockId", "serverId")
    }
  }
}
