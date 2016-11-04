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

package scheduler.onlinetesting

import java.util.concurrent.TimeUnit

import config.ScheduledJobConfigurable

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.Try

trait BasicJobConfig[T <: ScheduledJobConfigurable] {

  val conf: T
  val configPrefix: String
  val name: String

  val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  lazy val lockId = conf.lockId.getOrElse(exception("lockId"))

  lazy val initialDelay = conf.initialDelaySecs.flatMap(toDuration).getOrElse(exception("initialDelaySecs"))
  lazy val configuredInterval = conf.intervalSecs.flatMap(toFiniteDuration).getOrElse(exception("intervalSecs"))
  // Extra 1 second allows mongo lock to be relinquished
  lazy val interval = configuredInterval.plus(Duration(1, TimeUnit.SECONDS))
  lazy val forceLockReleaseAfter = configuredInterval

  private def toFiniteDuration(v: Int) = Try(FiniteDuration(v, TimeUnit.SECONDS)).toOption
  private def toDuration(v: Int) = Try(Duration(v, TimeUnit.SECONDS)).toOption
  def exception(propertyName: String) = throw new IllegalStateException(s"$configPrefix$propertyName config value not set")
}
