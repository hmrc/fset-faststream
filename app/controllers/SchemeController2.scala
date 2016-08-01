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

import config.CSRHttp
import connectors.ApplicationClient
import _root_.forms.SchemeSelectionForm
import _root_.forms.SchemeSelectionForm._
import security.Roles.{PersonalDetailsRole, SchemesRole}

import scala.concurrent.Future

object SchemeController2 extends SchemeController2(ApplicationClient) {
  val http = CSRHttp
}

// scalastyle:off
abstract class SchemeController2(applicationClient: ApplicationClient) extends BaseController(applicationClient) {

  def present = CSRSecureAppAction(SchemesRole) { implicit request =>
    implicit user =>
      val form = SchemeSelectionForm.form.fill(SchemeSelectionForm.EmptyData)
      Future.successful(Ok(views.html.application.schemeSelection(form)))
  }

  def submit = CSRSecureAppAction(PersonalDetailsRole) { implicit request =>
    implicit user =>
      SchemeSelectionForm.form.bindFromRequest.fold(
        errorForm => {
         ???
        },
        s => {
          println(s)
          Future.successful(Ok(views.html.application.schemeSelection(form)))
        }
    )
  }
}
