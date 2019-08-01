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

import com.mohiva.play.silhouette.api.Silhouette
import models.UniqueIdentifier
import models.page.Phase3FeedbackPage
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, AnyContent}
import security.Roles.ActiveUserRole
import security.{SecurityEnvironment, SilhouetteComponent}
import services.Phase3FeedbackService

object Phase3FeedbackController extends Phase3FeedbackController(Phase3FeedbackService) {
  lazy val silhouette: Silhouette[SecurityEnvironment] = SilhouetteComponent.silhouette
}

abstract class Phase3FeedbackController(phase3FeedbackService: Phase3FeedbackService) extends BaseController {

  def present: Action[AnyContent] = CSRSecureAction(ActiveUserRole) {
    implicit request =>
      implicit cachedData =>
        for {
          feedbackOpt <- phase3FeedbackService.getFeedback(cachedData.application.get.applicationId)
        } yield {
          Ok(views.html.home.phase3Feedback(feedbackOpt.map(Phase3FeedbackPage(_))))
        }
  }
}
