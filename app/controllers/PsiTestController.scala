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
import play.api.i18n.Messages
import play.api.mvc.{ Action, AnyContent }
import security.Roles.{ OnlineTestInvitedRole, Phase2TestInvitedRole }
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

  def startPhase2Tests = CSRSecureAppAction(Phase2TestInvitedRole) { implicit request =>
    implicit cachedUserData =>
      applicationClient.getPhase2TestProfile2(cachedUserData.application.applicationId).flatMap { phase2TestProfile =>
        startPsiTest(phase2TestProfile.activeTest :: Nil)
      }
  }

  private def startPsiTest(psiTests: Iterable[PsiTest])(implicit hc: HeaderCarrier) = {
    psiTests.find(!_.completed).map { testToStart =>
      applicationClient.startTest(testToStart.orderId.toString)
      Future.successful(Redirect(testToStart.testUrl))
    }.getOrElse(Future.successful(NotFound))
  }

  def completePhase1Tests(orderId: UniqueIdentifier): Action[AnyContent] = CSRUserAwareAction { implicit request =>
    implicit user =>
      val appId = user.flatMap { data =>
        data.application.map { application =>
          application.applicationId
        }
      }.getOrElse(throw new Exception("Unable to find applicationId for this candidate."))

      applicationClient.completeTestByOrderId(orderId).flatMap { _ =>
        applicationClient.getPhase1TestProfile2(appId).map { testGroup =>
          val testCompleted = testGroup.tests.find(_.orderId == orderId)
            .getOrElse(throw new Exception(s"Test not found for OrderId $orderId"))
          val testCompletedName = Messages(s"tests.inventoryid.name.${testCompleted.inventoryId}")

          if(incompleteTestsExists(testGroup.tests)) {
            Ok(views.html.application.onlineTests.continuePhase1Tests(testCompletedName))
          } else {
            Ok(views.html.application.onlineTests.phase1TestsComplete())
          }
        }
      }
  }

  def completePhase2Tests(orderId: UniqueIdentifier): Action[AnyContent] = CSRUserAwareAction { implicit request =>
    implicit user =>

      val appId = user.flatMap { data =>
        data.application.map { application =>
          application.applicationId
        }
      }.getOrElse(throw new Exception("Unable to find applicationId for this candidate."))

      applicationClient.completeTestByOrderId(orderId).flatMap { _ =>
        applicationClient.getPhase2TestProfile2(appId).map { _ =>
          Ok(views.html.application.onlineTests.etrayTestsComplete())
        }
      }
  }

  private def incompleteTestsExists(tests: Seq[PsiTest]): Boolean = {
    tests.exists(test => test.usedForResults && test.completedDateTime.isEmpty)
  }
}
