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

package testkit

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.language.postfixOps
import org.scalatest.matchers.must.Matchers

abstract class IntegrationSpec
  extends AnyWordSpec with Matchers with OptionValues with Inside with Inspectors with ScalaFutures {
  // System-wide setting for integration test timeouts.
  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(5000, Millis)))
}
