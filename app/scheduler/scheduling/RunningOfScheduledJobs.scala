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

import akka.actor.Scheduler
import play.api.inject.ApplicationLifecycle
import play.api.{Application, Logging}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * All implementing classes must be singletons - see
  * https://www.playframework.com/documentation/2.6.x/ScalaDependencyInjection#Stopping/cleaning-up
  */
trait RunningOfScheduledJobs extends Logging {

  implicit val ec: ExecutionContext

  val application: Application

  lazy val scheduler: Scheduler = application.actorSystem.scheduler

  val scheduledJobs: Seq[ScheduledJob]

  val applicationLifecycle: ApplicationLifecycle

  // Accessible for testing purposes
  private[scheduling] val cancellables = scheduledJobs.map { job =>
    val initialDelay = job.initialDelay
    val interval     = job.interval
    logger.info(s"Executing ${job.name}, running every $interval (after an initial delay of $initialDelay)")
    val cancellable =
      scheduler.scheduleWithFixedDelay(initialDelay, interval)(
        () => {
          val start = System.currentTimeMillis
          logger.info(s"Scheduler ${job.name} started")

          job.execute.onComplete {
            case Success(job.Result(message)) =>
              logger.info(s"Scheduler ${job.name} finished - took ${System.currentTimeMillis - start} millis, result = $message")
            case Failure(throwable) =>
              logger.error(
                s"Exception running job ${job.name} after ${System.currentTimeMillis - start} millis because: ${throwable.getMessage}",
                throwable
              )
          }
        }
      )
    applicationLifecycle.addStopHook(() => Future {
      logger.info(s"Checking if job ${job.name} is running before cancelling...")
      while (Await.result(job.isRunning, 5.seconds)) {
        logger.warn(s"Waiting for job ${job.name} to finish before cancelling")
        Thread.sleep(1000)
      }
      logger.warn(s"Job ${job.name} is not running - now calling cancel")
      cancellable.cancel()
    })
    cancellable
  }
}
