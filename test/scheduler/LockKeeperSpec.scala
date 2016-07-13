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

package scheduler

import org.joda.time.Duration
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import repositories.LockRepository
import scheduled.LockKeeper

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.postfixOps

class LockKeeperSpec extends PlaySpec with MockitoSugar {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val lockRepository = mock[LockRepository]

  class TestableLockKeeper extends LockKeeper {
    val repo: LockRepository = lockRepository
    val lockId = "lockId"
    val serverId = "serverId"
    val forceLockReleaseAfter = Duration.millis(1000L)
    val greedyLockingEnabled = true
  }

  val lockKeeper = new TestableLockKeeper
  val timeout = 5 seconds
  def testMethodForTheLockKeeper()(implicit ec: ExecutionContext): Future[String] = Future.successful("test result")

  "lockKeeper is locked" should {
    "return true if a lock exists in the repo" in {
      when(lockRepository.isLocked("lockId", "serverId")).thenReturn(Future.successful(true))
      val result = Await.result(lockKeeper.isLocked, timeout)
      result must be(true)
    }

    "return false if a lock does not exist in the repo" in {
      when(lockRepository.isLocked("lockId", "serverId")).thenReturn(Future.successful(false))
      val result = Await.result(lockKeeper.isLocked, timeout)
      result must be(false)
    }
  }

  "lockKeeper lock" should {
    "be acquired if the lock does not exist" in {
      when(lockRepository.lock(eqTo("lockId"), eqTo("serverId"), any[Duration])(any[ExecutionContext]))
        .thenReturn(Future.successful(false))

      val result = Await.result(lockKeeper.tryLock(testMethodForTheLockKeeper)(ec), timeout)
      result must be(None)
    }

    "be acquired if the lock exists but it has expired already" in {
      when(lockRepository.lock(eqTo("lockId"), eqTo("serverId"), any[Duration])(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(lockRepository.releaseLock("lockId", "serverId")).thenReturn(Future.successful(()))

      val result = Await.result(lockKeeper.tryLock(testMethodForTheLockKeeper)(ec), timeout)
      result must be(Some("test result"))
    }

    "fail when the repo throws an exception" in {
      when(lockRepository.lock(eqTo("lockId"), eqTo("serverId"), any[Duration])(any[ExecutionContext]))
        .thenReturn(Future.failed(new RuntimeException("test exception")))
      when(lockRepository.releaseLock("lockId", "serverId")).thenReturn(Future.successful(()))

      val result = Await.result(lockKeeper.tryLock(testMethodForTheLockKeeper)(ec).failed, timeout)

      verify(lockRepository, atLeastOnce()).releaseLock("lockId", "serverId")(ec)
      result.isInstanceOf[RuntimeException] must be(true)
      result.getMessage must be("test exception")
    }
  }
}
