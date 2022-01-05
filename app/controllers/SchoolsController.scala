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
import connectors.SchoolsClient
import connectors.SchoolsClient.SchoolsNotFound
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import models.view.SchoolView
import models.view.SchoolView.{ SchoolImplicits, _ }
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import security.QuestionnaireRoles.EducationQuestionnaireRole
import security.SilhouetteComponent

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.reflectiveCalls

@Singleton
class SchoolsController @Inject() (config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  schoolsClient: SchoolsClient)(implicit val ec: ExecutionContext)
  extends BaseController(config, mcc) {
  def getSchools(term: String) = CSRSecureAppAction(EducationQuestionnaireRole) { implicit request =>
    implicit user =>
      if (term.trim.nonEmpty) {
        schoolsClient.getSchools(term).map { schools =>
          val schoolViewList = schools match {
            case _ if schools.size > limitResults => narrowYourSearchHint +: schools.map(_.toSchoolView).take(limitResults)
            case _ => schools.map(_.toSchoolView)
          }
          Ok(Json.toJson(schoolViewList))
        }.recover {
          case _: SchoolsNotFound => BadRequest("could not locate school list")
        }
      } else {
        Future.successful(Ok(Json.toJson(List.empty[SchoolView])))
      }
  }
}
