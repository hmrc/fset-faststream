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
import play.api.mvc.{ Action, AnyContent }
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

        // TODO: In the combined world, we should render this template if there are more tests to be taken
        Ok(views.html.application.onlineTests.sjqComplete_continuePhase1Tests())
      }
  }

  def completePhase1Tests(orderId: UniqueIdentifier) = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTestByOrderId(orderId).map { _ =>
        //TODO:
        // We need to decide here if tests have been completed or not
        // We need a way to know if there are more tests to be completed.
        // Perhaps pull the test group here and check for active tests without completion date?
        // -> Ask Vijay: Can we have multiple active tests? E.g Do we have sjq and bq at the same
        // time in the test group?

        // TODO: In the combined world, we should render this template when there is no longer an active test
        Ok(views.html.application.onlineTests.phase1TestsComplete())
      }
  }

  //TODO: Complete this and replace the one above
  def completePhase1Tests2(orderId: UniqueIdentifier): Action[AnyContent] = CSRUserAwareAction { implicit request =>
    implicit user =>
//      applicationClient.completeTestByOrderId(orderId).map { _ =>
//        user.flatMap(_.application.map(_.applicationId)).map { appId =>
//          applicationClient.getPhase1Tests(appId).map { tests =>
//            if (incompleteTestsExists(tests)) {
//              //TODO: Redirect to page showing a test still exists
//              Ok()
//            } else {
//              //TODO: Redirect to page showing tests are complete
//              Ok()
//            }
//          }
//        }
//        //
//      }
      Future.successful(Ok(views.html.application.onlineTests.phase1TestsComplete()))
  }

  private def incompleteTestsExists(tests: Seq[PsiTest]): Boolean = {
    tests.exists(test => test.usedForResults && test.completedDateTime.isDefined)
  }
}
