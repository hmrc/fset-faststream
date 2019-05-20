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

package controllers

import config.CSRHttp
import connectors.ApplicationClient
import connectors.exchange.PsiTest
import models.UniqueIdentifier
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import security.Roles.OnlineTestInvitedRole
import security.SilhouetteComponent
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

object PsiTestController extends PsiTestController(ApplicationClient) {
  val http = CSRHttp
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class PsiTestController(applicationClient: ApplicationClient) extends BaseController {

  def startPhase1Tests = CSRSecureAppAction(OnlineTestInvitedRole) { implicit request =>
    implicit cachedUserData =>
      applicationClient.getPhase1TestProfile2(cachedUserData.application.applicationId).flatMap { phase1TestProfile =>
        startPsiTest(phase1TestProfile.tests)
      }
  }

  private def startPsiTest(psiTests: Iterable[PsiTest])(implicit hc: HeaderCarrier) = {
    psiTests.find(!_.completed).map { testToStart =>
      applicationClient.startTest(testToStart.orderId.toString)
      Future.successful(Redirect(testToStart.testUrl))
    }.getOrElse(Future.successful(NotFound))
  }

  def completeSjqAndContinuePhase1Tests(orderId: UniqueIdentifier) = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTestByOrderId(orderId).map { _ =>
        Ok(views.html.application.onlineTests.sjqComplete_continuePhase1Tests())
      }
  }

  def completePhase1Tests(orderId: UniqueIdentifier) = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTestByOrderId(orderId).map { _ =>
        Ok(views.html.application.onlineTests.phase1TestsComplete())
      }
  }
}
