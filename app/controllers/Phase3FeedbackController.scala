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

import config.{ FrontendAppConfig, SecurityEnvironment }
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import models.page.Phase3FeedbackPage
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents }
import security.Roles.Phase3TestDisplayFeedbackRole
import security.SilhouetteComponent
import services.Phase3FeedbackService

import scala.concurrent.ExecutionContext

@Singleton
class Phase3FeedbackController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  phase3FeedbackService: Phase3FeedbackService)
  (implicit val ec: ExecutionContext) extends BaseController(config, mcc) {

  def present: Action[AnyContent] = CSRSecureAppAction(Phase3TestDisplayFeedbackRole) {
    implicit request =>
      implicit user =>
        for {
          feedbackOpt <- phase3FeedbackService.getFeedback(user.application.applicationId)
        } yield {
          Ok(views.html.home.phase3Feedback(Phase3FeedbackPage(feedbackOpt.get)))
        }
  }
}
