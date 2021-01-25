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

import config.{FrontendAppConfig, SecurityEnvironment}
import connectors.ApplicationClient
import connectors.exchange.PsiTest
import helpers.NotificationTypeHelper

import javax.inject.{Inject, Singleton}
import models.UniqueIdentifier
import play.api.{Logger, Logging}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import security.Roles.{OnlineTestInvitedRole, Phase2TestInvitedRole, SiftNumericTestRole}
import security.SilhouetteComponent
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PsiTestController @Inject() (config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  applicationClient: ApplicationClient)(implicit val ec: ExecutionContext) extends BaseController(config, mcc) {

  def startPhase1Tests: Action[AnyContent] = CSRSecureAppAction(OnlineTestInvitedRole) { implicit request =>
    implicit cachedUserData =>
      applicationClient.getPhase1TestProfile2(cachedUserData.application.applicationId).flatMap { phase1TestProfile =>
        startPsiTest(phase1TestProfile.activeTests)
      }
  }

  def startPhase2Tests: Action[AnyContent] = CSRSecureAppAction(Phase2TestInvitedRole) { implicit request =>
    implicit cachedUserData =>
      applicationClient.getPhase2TestProfile2(cachedUserData.application.applicationId).flatMap { phase2TestProfile =>
        startPsiTest(phase2TestProfile.activeTests)
      }
  }

  def startSiftNumericTest: Action[AnyContent] = CSRSecureAppAction(SiftNumericTestRole) { implicit request =>
    implicit cachedUserData =>
      applicationClient.getSiftTestGroup2(cachedUserData.application.applicationId).flatMap { siftTestGroup =>
        val tests = siftTestGroup.activeTest :: Nil
        tests.find(!_.completed).map { testToStart =>
          if (!testToStart.started) {
            applicationClient.startSiftTest(testToStart.orderId.toString)
          }
          Future.successful(Redirect(testToStart.testUrl))
        }.getOrElse(Future.successful(NotFound))
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
      applicationClient.completeTestByOrderId(orderId).flatMap { _ =>
        applicationClient.getPhase1TestGroupWithNames2ByOrderId(orderId).map { testGroup =>
          val testCompleted = testGroup.activeTests.find(_.orderId == orderId)
            .getOrElse(throw new Exception(s"Test not found for OrderId $orderId"))
          val testCompletedName = Messages(s"tests.inventoryid.name.${testCompleted.inventoryId}")

          if(incompleteTestsExists(testGroup.activeTests)) {
            Ok(views.html.application.onlineTests.continueTests(testCompletedName))
          } else {
            Ok(views.html.application.onlineTests.phase1TestsComplete())
          }
        }
      }.recover {
        case ex: Throwable =>
          logger.warn("Exception when completing phase 1 tests", ex)
          InternalServerError(s"Unable to complete phase 1 test for orderId=$orderId because ${ex.getMessage}")
      }
  }

  // Note that we do not work with the applicationId in the cached user data because if we are dealing with an
  // invigilated phase 2 candidate, the candidate may not already be logged in so the cached data is missing
  def completePhase2Tests(orderId: UniqueIdentifier): Action[AnyContent] = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTestByOrderId(orderId).flatMap { _ =>
        applicationClient.getPhase2TestProfile2ByOrderId(orderId).map { testGroup =>
          val testCompleted = testGroup.activeTests.find(_.orderId == orderId)
            .getOrElse(throw new Exception(s"Active Test not found for OrderId $orderId"))
          val testCompletedName = Messages(s"tests.inventoryid.name.${testCompleted.inventoryId}")

          if(incompleteTestsExists(testGroup.activeTests)) {
            Ok(views.html.application.onlineTests.continueTests(testCompletedName))
          } else {
            Ok(views.html.application.onlineTests.workBasedScenariosTestsComplete())
          }
        }
      }.recover {
        case ex: Throwable =>
          logger.warn("Exception when completing phase 2 tests", ex)
          InternalServerError(s"Unable to complete phase 2 test for orderId=$orderId because ${ex.getMessage}")
      }
}

  def completeSiftTest(orderId: UniqueIdentifier): Action[AnyContent] = CSRUserAwareAction { implicit request =>
    implicit user =>
      applicationClient.completeTestByOrderId(orderId).map { _ =>
        Ok(views.html.application.onlineTests.siftTestComplete())
      }
  }

  private def incompleteTestsExists(tests: Seq[PsiTest]): Boolean = {
    tests.exists(test => test.usedForResults && test.completedDateTime.isEmpty)
  }
}
