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

package scheduler.scheduling

import testkit.UnitSpec

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class ExclusiveScheduledJobSpec extends UnitSpec {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  class TestJob extends ExclusiveScheduledJob {
    val start = new CountDownLatch(1)

    def startExecution(): Unit = start.countDown()

    val executionCount = new AtomicInteger(0)

    def executions: Int = executionCount.get()

    override def executeInMutex(implicit ec: ExecutionContext): Future[Result] =
      Future {
        start.await(1, TimeUnit.MINUTES) // Will block for 1 minute if the CountDownLatch is not zero
        Result(executionCount.incrementAndGet().toString)
      }

    override def name = "testJob"

    override def initialDelay = FiniteDuration(1, TimeUnit.MINUTES)

    override def interval = FiniteDuration(1, TimeUnit.MINUTES)
  }

  "ExclusiveScheduledJob" should {
    import scala.concurrent.ExecutionContext.Implicits.global

    "let each job run in sequence" in {
      val job = new TestJob
      job.startExecution()
      job.execute.futureValue.message mustBe "1"
      job.execute.futureValue.message mustBe "2"
    }

    "not allow jobs to run in parallel" in {
      val job = new TestJob

      val pausedExecution = job.execute // This will block because the CountDownLatch is not zero
      pausedExecution.isCompleted     mustBe false
      job.isRunning.futureValue       mustBe true // The job is running but blocked on the CountDownLatch
      // The mutex is not re-entrant so the 2nd invocation of execute on the same job in the same thread fails to acquire the mutex
      job.execute.futureValue.message mustBe "Skipping execution: another job is running"
      job.isRunning.futureValue       mustBe true // The job is still running and still blocked on the CountDownLatch

      job.startExecution() // Now allow the job to run by decrementing the CountDownLatch
      pausedExecution.futureValue.message mustBe "1"
      job.isRunning.futureValue           mustBe false
    }

    "should handle exceptions in execution" in {
      val job = new TestJob() {
        override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = throw new RuntimeException
      }

      val exception = job.execute.failed.futureValue
      exception mustBe an[RuntimeException]
      job.isRunning.futureValue mustBe false
    }
  }
}
