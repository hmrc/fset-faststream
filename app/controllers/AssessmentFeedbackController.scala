/*
 * Copyright 2022 HM Revenue & Customs
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

import com.mohiva.play.silhouette.api.Silhouette
import config.{ FrontendAppConfig, SecurityEnvironment }
import connectors.{ ApplicationClient, AssessmentScoresClient, UserManagementClient }
import javax.inject.{ Inject, Singleton }
import models.UniqueIdentifier
import models.page.AssessmentFeedbackPage
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents }
import security.Roles.ActiveUserRole
import security.SilhouetteComponent
import helpers.NotificationTypeHelper

import scala.concurrent.ExecutionContext

@Singleton
class AssessmentFeedbackController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  assessmentScoresClient: AssessmentScoresClient,
  applicationClient: ApplicationClient)(implicit val ec: ExecutionContext) extends BaseController(config, mcc) {
  import notificationTypeHelper._

  def present(applicationId: UniqueIdentifier): Action[AnyContent] = CSRSecureAction(ActiveUserRole) {
    implicit request =>
      implicit cachedData =>
        for {
          reviewerScoresAndFeedback <- assessmentScoresClient
            .findReviewerAcceptedAssessmentScores(applicationId)
          evaluatedAverageResults <- applicationClient.findFsacEvaluationAverages(applicationId)
          personalDetails <- applicationClient.getPersonalDetails(cachedData.user.userID, applicationId)
        } yield {
          val name = s"${personalDetails.firstName} ${personalDetails.lastName}"
          val page = AssessmentFeedbackPage(reviewerScoresAndFeedback, evaluatedAverageResults, name)
          Ok(views.html.home.assessmentFeedback(page))
        }
  }
}
