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

import connectors.SchoolsClient
import connectors.SchoolsClient.SchoolsNotFound
import models.view.SchoolView
import models.view.SchoolView._
import models.view.SchoolView.SchoolImplicits
import play.api.libs.json.Json
import security.QuestionnaireRoles.EducationQuestionnaireRole
import security.SilhouetteComponent

import scala.concurrent.Future
import scala.language.reflectiveCalls

object SchoolsController extends SchoolsController(SchoolsClient) {
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class SchoolsController(schoolsClient: SchoolsClient)
  extends BaseController {
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
