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

package controllers.reference

import javax.inject.{ Inject, Singleton }
import model.exchange.FsacAssessmentCentres
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import repositories.csv.FSACIndicatorCSVRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class FsacIndicatorController @Inject() (cc: ControllerComponents,
                                         fsacIndicatorRepository: FSACIndicatorCSVRepository) extends BackendController(cc) {

  def getAssessmentCentres: Action[AnyContent] = Action { implicit request =>
    Ok(Json.toJson(FsacAssessmentCentres(fsacIndicatorRepository.getAssessmentCentres)))
  }
}
