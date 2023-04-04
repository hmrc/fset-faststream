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

package repositories

import org.joda.time.Duration
import testkit.MongoRepositorySpec

class LockRepositorySpec extends MongoRepositorySpec {
  val lockTimeout = new Duration(1000L)
  val lockTimeoutJavaTime = java.time.Duration.ofMillis(1000L)

  override val collectionName: String = CollectionNames.LOCKS

  def repo = new LockMongoRepository(mongo)

  "Lock Repository" should {
    "create indexes" in {
      val indexes = indexDetails(repo).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "owner_1", keys = Seq(("owner", "Ascending")), unique = false),
          IndexDetails(name = "timeCreated_1", keys = Seq(("timeCreated", "Ascending")), unique = false),
          IndexDetails(name = "expiryTime_1", keys = Seq(("expiryTime", "Ascending")), unique = false)
        )
    }

    "insert a lock when the database is empty" in {
      val result = repo.lock("lockId", "owner", lockTimeoutJavaTime).futureValue
      result mustBe true
    }

    "fail to insert another lock when the first one has not yet expired" in {
      repo.lock("lockId", "owner", lockTimeoutJavaTime).futureValue
      val result = repo.lock("lockId", "owner", lockTimeoutJavaTime).futureValue
      result mustBe false
    }

    "be locked when one lock has expired, but another one has been created afterwards" in {
      repo.lock("lockId", "owner", java.time.Duration.ofMillis(500L)).futureValue
      Thread.sleep(505L) // Wait for the lock to expire (5 millis longer than the lock duration)
      repo.lock("lockId", "owner", java.time.Duration.ofMillis(500L)).futureValue
      val isLocked = repo.isLocked("lockId", "owner").futureValue
      isLocked mustBe true
    }

    "is not locked when the lock has expired" in {
      repo.lock("lockId", "owner", java.time.Duration.ofMillis(500L)).futureValue
      Thread.sleep(501L)
      val result = repo.isLocked("lockId", "owner").futureValue
      result mustBe false
    }

    "has no lock when the lock has been released" in {
      repo.lock("lockId", "owner", lockTimeoutJavaTime).futureValue
      repo.releaseLock("lockId", "owner").futureValue
      val result = repo.isLocked("lockId", "owner").futureValue
      result mustBe false
    }
  }
}
