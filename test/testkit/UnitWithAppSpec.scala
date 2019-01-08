/*
 * Copyright 2019 HM Revenue & Customs
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

import akka.stream.Materializer
import com.kenshoo.play.metrics.PlayModule
import controllers.UnitSpec
import org.scalatestplus.play.OneAppPerSuite
import play.api.{ Application, Play }
import play.api.inject.guice.GuiceApplicationBuilder

abstract class UnitWithAppSpec extends UnitSpec with OneAppPerSuite {

  // Suppress logging during tests
  val additionalConfig = Map("logger.application" -> "ERROR")

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(new SilhouetteFakeModule())
    .disable[PlayModule]
    .configure(additionalConfig)
    .build

  implicit def mat: Materializer = Play.materializer(app)
}
