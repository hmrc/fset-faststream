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

import javax.inject.{ Inject, Singleton }
import model.persisted.PsiTest
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import repositories.onlinetesting._
import services.onlinetesting.OnlineTestExtensionService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class Phase1TestGroupController @Inject() (cc: ControllerComponents,
                                           phase1Repository: Phase1TestRepository,
                                           phase1TestExtensionService: OnlineTestExtensionService) extends BackendController(cc) {

  def getTests(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    phase1Repository.getTestGroup(applicationId).map { maybeTestProfile =>
      maybeTestProfile
        .map(testProfile => Ok(Json.toJson(testProfile.tests)))
        .getOrElse(Ok(Json.toJson(Seq.empty[PsiTest])))
    } //TODO: perhaps recover here if there are issues?
  }

  def extend(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestExtension] { extension =>
      phase1TestExtensionService.extendTestGroupExpiryTime(applicationId, extension.extraDays,
        extension.actionTriggeredBy
      ).map( _ => Ok )
    }
  }
}
