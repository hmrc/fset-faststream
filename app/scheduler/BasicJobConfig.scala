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

import java.util.concurrent.TimeUnit

import config.ScheduledJobConfigurable
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import play.api.Configuration

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.Try

case class BasicJobConfig[T <: ScheduledJobConfigurable] (config: Configuration, configPrefix: String, name: String)
                                                         (implicit reader: ValueReader[T]) {

  lazy val conf: T = config.underlying.as[T](configPrefix)

  def lockId = conf.lockId.getOrElse(exception("lockId"))

  def initialDelay = conf.initialDelaySecs.flatMap(toDuration).getOrElse(exception("initialDelaySecs"))
  def configuredInterval = conf.intervalSecs.flatMap(toFiniteDuration).getOrElse(exception("intervalSecs"))
  // Extra 1 second allows mongo lock to be relinquished
  def interval = configuredInterval.plus(Duration(1, TimeUnit.SECONDS))
  def forceLockReleaseAfter = configuredInterval
  def enabled = conf.enabled

  private def toFiniteDuration(v: Int) = Try(FiniteDuration(v, TimeUnit.SECONDS)).toOption
  private def toDuration(v: Int) = Try(Duration(v, TimeUnit.SECONDS)).toOption
  def exception(propertyName: String) = throw new IllegalStateException(s"$configPrefix.$propertyName config value not set")
}
