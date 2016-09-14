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

import model.exchange.Phase1TestResultReady
import play.api.mvc.Action
import uk.gov.hmrc.play.microservice.controller.BaseController
import play.api.Logger
import scala.concurrent.Future

object Phase1TestsController extends Phase1TestsController {

}

trait Phase1TestsController extends BaseController {

  def startAssessment(assessmentId: String) = Action.async(parse.json) { implicit request =>
    Logger.info(s"Assessment $assessmentId started")
    Future.successful(Ok)
  }

  def completeAssessment(assessmentId: String) = Action.async(parse.json) { implicit request =>
    Logger.info(s"Assessment $assessmentId completed")
    Future.successful(Ok)
  }

  def markResultsReady(assessmentId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[Phase1TestResultReady] { phase1TestResultReady =>
      Logger.info(s"Assessment $assessmentId has report [$phase1TestResultReady] ready to download")
      Future.successful(Ok)
    }
  }

}
