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

import config.{FrontendAppConfig, SecurityEnvironment}
import connectors.ApplicationClient
import connectors.ApplicationClient.OnlineTestNotFound
import helpers.NotificationTypeHelper
import javax.inject.{Inject, Singleton}
import models.ApplicationData
import models.page.{Phase1TestsPage, Phase2TestsPage2, Phase3TestsPage, TestResultsPage}
import play.api.mvc.MessagesControllerComponents
import security.Roles.PreviewApplicationRole
import security.SilhouetteComponent
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestResultsController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  applicationClient: ApplicationClient)(implicit val ec: ExecutionContext) extends BaseController(config, mcc) {

  def present = CSRSecureAppAction(PreviewApplicationRole) { implicit request =>
    implicit user =>
      implicit val applicationData = user.application

      val phase1DataOptFut = getPhase1Test
      val phase2DataOptFut = getPhase2Test
      val phase3DataOptFut = getPhase3Test

      for {
        phase1TestsWithNames <- phase1DataOptFut
        phase2TestsWithNames <- phase2DataOptFut
        phase3Tests <- phase3DataOptFut
      } yield {
        val phase1DataOpt = phase1TestsWithNames.map(Phase1TestsPage(_))
        val phase2DataOpt = phase2TestsWithNames.map(Phase2TestsPage2(_, None))
        val phase3DataOpt = phase3Tests.map(Phase3TestsPage(_, None))

        val page = TestResultsPage(phase1DataOpt, phase2DataOpt, phase3DataOpt)
        Ok(views.html.application.onlineTests.testResults(page))
      }
  }

  private def getPhase1Test(implicit application: ApplicationData, hc: HeaderCarrier) =
    applicationClient.getPhase1TestProfile2(application.applicationId).map(Some(_)).recover {
      case _: OnlineTestNotFound =>
        None
    }

  private def getPhase2Test(implicit application: ApplicationData, hc: HeaderCarrier) = if (application.isPhase2) {
    applicationClient.getPhase2TestProfile2(application.applicationId).map(Some(_))
  } else {
    Future.successful(None)
  }

  private def getPhase3Test(implicit application: ApplicationData, hc: HeaderCarrier) = if (application.isPhase3) {
    applicationClient.getPhase3TestGroup(application.applicationId).map(Some(_))
  } else {
    Future.successful(None)
  }
}
