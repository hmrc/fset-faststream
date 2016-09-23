/*
 * Copyright 2016 HM Revenue & Customs
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
import play.api.libs.json.Json
import security.QuestionnaireRoles.EducationQuestionnaireRole
import security.SecureActions
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.language.reflectiveCalls

object SchoolsController extends SchoolsController(SchoolsClient)

class SchoolsController(schoolsClient: SchoolsClient) extends FrontendController with SecureActions {

  def getSchools(term: String) = CSRSecureAppAction(EducationQuestionnaireRole) { implicit request =>
    implicit user =>
      schoolsClient.getSchools(term).map { results =>
        Ok(Json.toJson(results))
      }.recover{
        case _: SchoolsNotFound => BadRequest("could not locate school list")
      }
  }
}
