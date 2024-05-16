/*
 * Copyright 2024 HM Revenue & Customs
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

import config.ScheduledJobConfig
import repositories.CollectionNames
import scheduler.BasicJobConfig
import testkit.MongoRepositorySpec

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

class SingleInstanceScheduledJobSpec extends MongoRepositorySpec {
  val collectionName: String = CollectionNames.LOCKS
  "SingeInstanceScheduledJob isRunning" should {
    "be true when executing" in {
      val promise = Promise[Unit]()
      val aLongTime = Duration(100, SECONDS)

      val job = new SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
        def config = ???
        override val lockId = "test lock id"
        override val forceLockReleaseAfter: FiniteDuration = aLongTime
        override def name = "Test Lock"

        override def initialDelay = Duration(200, MILLISECONDS)
        override def interval: FiniteDuration = aLongTime

        override val mongoComponent = mongo

        override implicit val ec: ExecutionContext = global

        def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
          isRunning.futureValue mustBe true
          promise.future
        }
      }

      job.isRunning.futureValue mustBe false
      promise.success(())
      job.isRunning.futureValue mustBe false
    }
  }
}
