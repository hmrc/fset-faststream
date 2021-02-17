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

package controllers

import config.{ FrontendAppConfig, SecurityEnvironment }
import connectors.ApplicationClient
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import play.api.mvc.MessagesControllerComponents
import security.Roles.Phase3TestInvitedRole
import security.SilhouetteComponent

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class LaunchpadTestController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  applicationClient: ApplicationClient)(implicit val ec: ExecutionContext) extends BaseController(config, mcc) {

  def startPhase3Tests = CSRSecureAppAction(Phase3TestInvitedRole) { implicit request =>
    implicit cachedUserData =>
      applicationClient.getPhase3TestGroup(cachedUserData.application.applicationId).flatMap { testProfile =>
        testProfile.activeTests.find(!_.completed).map { testToStart =>
          // Mark the test as started, if this is the first time they've clicked the button
          if (testToStart.startedDateTime.isEmpty) {
            applicationClient.startPhase3TestByToken(testToStart.token).map { _ =>
              Redirect(testToStart.testUrl)
            }
          } else {
            Future.successful(Redirect(testToStart.testUrl))
          }
        }.getOrElse(Future.successful(NotFound))
      }
  }

  def completePhase3TestsByToken(token: String) = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completePhase3TestByToken(token).map { _ =>
        Ok(views.html.application.onlineTests.phase3TestsComplete())
      }
  }
}
