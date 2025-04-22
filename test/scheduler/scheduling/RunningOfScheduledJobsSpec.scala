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

package scheduler.scheduling

import org.apache.pekko.actor.{Cancellable, Scheduler}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Minute, Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.ApplicationLifecycle
import testkit.UnitSpec

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class RunningOfScheduledJobsSpec extends UnitSpec with Eventually with GuiceOneAppPerTest {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds)

  "When starting the app, the scheduled job runner" should {

    "schedule a configured job with the given interval and initialDuration" in new TestCase {
      object Captured {
        var initialDelay: FiniteDuration = _
        var interval: FiniteDuration     = _
      }

      val applicationLifecycle: ApplicationLifecycle = app.injector.instanceOf[ApplicationLifecycle]
      implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

      // Instantiate the trait with an anonymous class
      new RunningOfScheduledJobs(app, applicationLifecycle) {
        override lazy val scheduledJobs: Seq[ScheduledJob] = Seq(testScheduledJob)

        override lazy val scheduler: Scheduler = new StubbedScheduler {
          override def scheduleWithFixedDelay(initialDelay: FiniteDuration, interval: FiniteDuration)(runnable: Runnable)
                                             (implicit executor: ExecutionContext): Cancellable = {
            Captured.initialDelay = initialDelay
            Captured.interval     = interval
            new Cancellable {
              override def cancel(): Boolean = true
              override def isCancelled: Boolean = false
            }
          }
        }
      }

      Captured must have(Symbol("initialDelay") (testScheduledJob.initialDelay))
      Captured must have(Symbol("interval") (testScheduledJob.interval))

      app.stop()
    }

    "set up the scheduled job to run the execute method" in new TestCase {
      var capturedRunnable: Runnable = _
      override val testScheduledJob = new TestScheduledJob {
        var executed = false
        override def execute(implicit ec: ExecutionContext): Future[Result] = {
          executed = true
          Future.successful(this.Result("Success"))
        }
        override def isExecuted: Boolean = executed
      }

      val applicationLifecycle: ApplicationLifecycle = app.injector.instanceOf[ApplicationLifecycle]
      implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
      new RunningOfScheduledJobs(app, applicationLifecycle) {
        override lazy val scheduler: Scheduler = new StubbedScheduler {
          override def scheduleWithFixedDelay(initialDelay: FiniteDuration, interval: FiniteDuration)(runnable: Runnable)
                               (implicit executor: ExecutionContext): Cancellable = {
            capturedRunnable = runnable
            new Cancellable {
              override def cancel(): Boolean = true
              override def isCancelled: Boolean = false
            }
          }
        }

        override lazy val scheduledJobs: Seq[ScheduledJob] = Seq(testScheduledJob)
      }

      testScheduledJob.isExecuted mustBe false
      capturedRunnable.run()
      testScheduledJob.isExecuted mustBe true

      app.stop()
    }
  }

  "When stopping the app, the scheduled job runner" should {
    "cancel all of the scheduled jobs" in new TestCase {
      val applicationLifecycle: ApplicationLifecycle = app.injector.instanceOf[ApplicationLifecycle]
      implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
      val runner = new RunningOfScheduledJobs(app, applicationLifecycle) {
        override lazy val scheduledJobs: Seq[ScheduledJob] = Seq(testScheduledJob)
      }

      every(runner.cancellables) must not be Symbol("cancelled")
      app.stop()
      eventually (timeout(Span(5, Seconds))) { every(runner.cancellables) mustBe Symbol("cancelled") }
    }

    "block while a scheduled job is still running" in new TestCase {
      val stoppableJob = new TestScheduledJob() {
        override def name: String = "StoppableJob"
      }
      val applicationLifecycle: ApplicationLifecycle = app.injector.instanceOf[ApplicationLifecycle]
      implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
      new RunningOfScheduledJobs(app, applicationLifecycle) {
        override lazy val scheduledJobs: Seq[ScheduledJob] = Seq(stoppableJob)
      }

      stoppableJob.isRunning = Future.successful(true)

      val deadline: Deadline = 5000.milliseconds.fromNow
      while (deadline.hasTimeLeft()) {
        /* Intentionally burning CPU cycles for fixed period */
      }

      val stopFuture = app.stop()
      stopFuture must not be Symbol("completed")

      stoppableJob.isRunning = Future.successful(false)
      eventually (timeout(Span(1, Minute))) { stopFuture mustBe Symbol("completed") }
    }
  }

  trait TestCase {
    class StubbedScheduler extends Scheduler {
      override def scheduleWithFixedDelay(initialDelay: FiniteDuration, interval: FiniteDuration)(runnable: Runnable)(
        implicit executor: ExecutionContext): Cancellable = new Cancellable {
        override def cancel(): Boolean = true
        override def isCancelled: Boolean = false
      }
      def maxFrequency: Double  = 1
      def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(implicit executor: ExecutionContext): Cancellable = new Cancellable {
        override def cancel(): Boolean = true
        override def isCancelled: Boolean = false
      }

      // Just here to satisfy the Scheduler trait
      override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)(
        implicit executor: ExecutionContext): Cancellable = ???
    }

    class TestScheduledJob extends ScheduledJob {
      override lazy val initialDelay: FiniteDuration = 0.seconds
      override lazy val interval: FiniteDuration = 3.seconds
      def name: String = "TestScheduledJob"
      def isExecuted: Boolean = true

      def execute(implicit ec: ExecutionContext): Future[Result] = Future.successful(Result("Success"))
      var isRunning: Future[Boolean] = Future.successful(false)
    }

    val testScheduledJob = new TestScheduledJob
  }
}
