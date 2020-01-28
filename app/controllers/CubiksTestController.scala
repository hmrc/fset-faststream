/*
 * Copyright 2020 HM Revenue & Customs
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
import connectors.exchange.CubiksTest
import models.UniqueIdentifier
import security.Roles.{ OnlineTestInvitedRole, Phase2TestInvitedRole, SiftNumericTestRole }
import security.SilhouetteComponent

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import uk.gov.hmrc.http.HeaderCarrier

object CubiksTestController extends CubiksTestController(ApplicationClient) {
  val http = CSRHttp
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class CubiksTestController(applicationClient: ApplicationClient)
  extends BaseController {

  def startPhase1Tests = CSRSecureAppAction(OnlineTestInvitedRole) { implicit request =>
    implicit cachedUserData =>
     applicationClient.getPhase1TestProfile(cachedUserData.application.applicationId).flatMap { phase1TestProfile =>
       startCubiksTest(phase1TestProfile.tests)
      }
  }

  def startPhase2Tests = CSRSecureAppAction(Phase2TestInvitedRole) { implicit request =>
    implicit cachedUserData =>
      applicationClient.getPhase2TestProfile(cachedUserData.application.applicationId).flatMap { phase2TestProfile =>
        startCubiksTest(phase2TestProfile.activeTest :: Nil)
      }
  }

  def completeSjqByTokenAndContinuePhase1Tests(token: UniqueIdentifier) = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTestByToken(token).map { _ =>
        Ok(views.html.application.onlineTests.sjqComplete_continuePhase1Tests())
      }
  }

  def completePhase1TestsByToken(token: UniqueIdentifier) = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTestByToken(token).map { _ =>
        Ok(views.html.application.onlineTests.phase1TestsComplete())
      }
  }

  def completePhase2TestsByToken(token: UniqueIdentifier) = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTestByToken(token).map { _ =>
        Ok(views.html.application.onlineTests.workBasedScenariosTestsComplete())
      }
  }

  def completeTestByToken(token: UniqueIdentifier) = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTestByToken(token).map { _ =>
        Ok(views.html.application.onlineTests.onlineTestSuccess())
      }
  }

  def startSiftNumericTest = CSRSecureAppAction(SiftNumericTestRole) { implicit request =>
    implicit cachedUserData =>
      applicationClient.getSiftTestGroup(cachedUserData.application.applicationId).flatMap { siftTestGroup =>
        val cubiksTests = siftTestGroup.activeTest :: Nil
        cubiksTests.find(!_.completed).map { testToStart =>
          if (!testToStart.started) {
            applicationClient.startSiftTest(testToStart.cubiksUserId)
          }
          Future.successful(Redirect(testToStart.testUrl))
        }.getOrElse(Future.successful(NotFound))
      }
  }

  def completeSiftTestByToken(token: UniqueIdentifier) = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTestByToken(token).map { _ =>
        Ok(views.html.application.onlineTests.siftTestComplete())
      }
  }

  private def startCubiksTest(cubiksTests: Iterable[CubiksTest])(implicit hc: HeaderCarrier) = {
    cubiksTests.find(!_.completed).map { testToStart =>
      applicationClient.startTest(testToStart.cubiksUserId)
      Future.successful(Redirect(testToStart.testUrl))
    }.getOrElse(Future.successful(NotFound))
  }
}
