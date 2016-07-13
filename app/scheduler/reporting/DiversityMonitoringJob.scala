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

package scheduler.reporting

import java.util.concurrent.TimeUnit

import scheduler.clustering.SingleInstanceScheduledJob
import services.reporting.DiversityMonitoringReport

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object DiversityMonitoringJob extends SingleInstanceScheduledJob with DiversityMonitoringJobConfig {
  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] =
    DiversityMonitoringReport.execute()
}

trait DiversityMonitoringJobConfig {
  this: SingleInstanceScheduledJob =>
  import config.MicroserviceAppConfig.{ diversityMonitoringJobConfig => config }
  val configPrefix = "scheduling.diversity-monitoring-job."

  val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val lockId = config.lockId.getOrElse(exception("lockId"))

  val name = "DiversityMonitoringJob"

  val initialDelay = config.initialDelaySecs.flatMap(toDuration).getOrElse(exception("initialDelaySecs"))
  val configuredInterval = config.intervalSecs.flatMap(toFiniteDuration).getOrElse(exception("intervalSecs"))
  // Extra 1 second allows mongo lock to be relinquish
  val interval = configuredInterval.plus(Duration(1, TimeUnit.SECONDS))
  val forceLockReleaseAfter = configuredInterval

  private def toFiniteDuration(v: Int) = Try(FiniteDuration(v, TimeUnit.SECONDS)).toOption
  private def toDuration(v: Int) = Try(Duration(v, TimeUnit.SECONDS)).toOption
  def exception(propertyName: String) = throw new IllegalStateException(s"$configPrefix.$propertyName config value not set")
}
