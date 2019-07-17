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
import org.scalatest.TestSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsValue, Json, Writes }
import play.api.mvc.Results
import play.api.test.{ FakeHeaders, FakeRequest }
import play.api.{ Application, Play }
import play.modules.reactivemongo.ReactiveMongoHmrcModule

/**
  * Common base class for all controller tests
  */
abstract class UnitWithAppSpec extends UnitSpec with WithAppSpec

abstract class ScalaMockUnitWithAppSpec extends ScalaMockUnitSpec with WithAppSpec

trait WithAppSpec extends GuiceOneAppPerSuite with Results with FutureHelper with ScalaFutures { this: TestSuite =>
  val AppId = "AppId"
  val UserId = "UserId"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .disable[PlayModule]
    .disable[ReactiveMongoHmrcModule]
    .build

  implicit def mat: Materializer = Play.materializer(app)

  // Suppress logging during tests
  def additionalConfig = Map("logger.application" -> "ERROR")

  def fakeRequest[T](request: T)(implicit tjs: Writes[T]): FakeRequest[JsValue] =
    FakeRequest("", "", FakeHeaders(), Json.toJson(request)).withHeaders("Content-Type" -> "application/json")

  def fakeRequest = FakeRequest()

}
