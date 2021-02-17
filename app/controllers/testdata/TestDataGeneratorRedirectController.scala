/*
 * Copyright 2021 HM Revenue & Customs
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

import config.{ FrontendAppConfig, SecurityEnvironment }
import connectors.{ TestDataGeneratorClient, UserManagementClient }
import controllers.BaseController
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import play.api.mvc.MessagesControllerComponents
import security.SilhouetteComponent

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class TestDataGeneratorRedirectController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val userManagementClient: UserManagementClient,
  val notificationTypeHelper: NotificationTypeHelper,
  testDataClient: TestDataGeneratorClient)(implicit val ec: ExecutionContext)
  extends BaseController(config, mcc) {

  @deprecated("Call the TDG using admin frontend, it is better supported")
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
