/*
 * Copyright 2018 HM Revenue & Customs
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

package controllers.testdata

import config.CSRHttp
import connectors.TestDataClient
import controllers.BaseController
import security.SilhouetteComponent

import scala.concurrent.Future

object TestDataGeneratorRedirectController extends TestDataGeneratorRedirectController(TestDataClient) {
  val http = CSRHttp
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class TestDataGeneratorRedirectController(testDataClient: TestDataClient)
  extends BaseController {

  def generateTestData(path: String) = CSRUserAwareAction { implicit request =>
    implicit user =>
      val queryParams = request.queryString.map { case (k, v) => k -> v.mkString }
      testDataClient.getTestDataGenerator(path, queryParams).map(Ok(_))
  }

  def ping = CSRUserAwareAction { implicit request =>
    implicit user =>
      Future.successful(Ok("OK"))
  }
}