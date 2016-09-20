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

package controllers

import config.CSRHttp
import connectors.ApplicationClient
import models.ApplicationData.{ ApplicationStatus, ProgressStatuses }
import models.UniqueIdentifier
import security.Roles.{ DisplayOnlineTestSectionRole, OnlineTestInvitedRole }

import scala.concurrent.Future

object OnlineTestController extends OnlineTestController(ApplicationClient) {
  val http = CSRHttp
}

abstract class OnlineTestController(applicationClient: ApplicationClient) extends BaseController(applicationClient) {

  def startPhase1Tests = CSRSecureAppAction(OnlineTestInvitedRole) { implicit request =>
    implicit cachedUserData =>

      applicationClient.getPhase1TestProfile(cachedUserData.user.userID).flatMap { testProfile =>
        // If we've started but not completed a test we still want to send them to that
        // test link to continue with it
        testProfile.tests.find(!_.completed).map { testToStart =>
          applicationClient.onlineTestUpdate(cachedUserData.application.applicationId,
            connectors.exchange.TestStatusUpdate(ProgressStatuses.PHASE1_TESTS_STARTED,
              testToStart.token
            )
          )
          Future.successful(Redirect(testToStart.testUrl))
        }.getOrElse(Future.successful(NotFound))
      }
  }

  def completeTestByToken(token: UniqueIdentifier) = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTests(token).map { _ =>
        Ok(views.html.application.onlineTestSuccess())
      }
  }

}
