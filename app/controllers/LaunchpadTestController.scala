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

package controllers

import config.CSRHttp
import connectors.ApplicationClient
import security.Roles.Phase3TestInvitedRole
import security.SilhouetteComponent

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

object LaunchpadTestController extends LaunchpadTestController(ApplicationClient) {
  val http = CSRHttp
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class LaunchpadTestController(applicationClient: ApplicationClient) extends BaseController {

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
