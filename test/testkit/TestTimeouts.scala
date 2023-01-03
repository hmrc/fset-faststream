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

package testkit

import org.scalatest.concurrent.AbstractPatienceConfiguration
import org.scalatest.time.{ Seconds, Span }

import scala.concurrent.duration._

trait TimeoutConfig extends AbstractPatienceConfiguration {
  ScalaFutures =>
  val timeoutSecs: Int
  val timeout = FiniteDuration(timeoutSecs.toLong, SECONDS)
  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(timeoutSecs, Seconds)))
}

trait ShortTimeout extends TimeoutConfig {
  val timeoutSecs = 3
}

trait ExtendedTimeout extends TimeoutConfig {
  val timeoutSecs = 10
}
