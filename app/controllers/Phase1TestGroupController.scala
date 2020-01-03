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

import model.persisted.PsiTest
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import repositories._
import repositories.onlinetesting.{ Phase1TestMongoRepository, Phase1TestMongoRepository2, Phase1TestRepository, Phase1TestRepository2 }
import services.onlinetesting.OnlineTestExtensionService
import services.stc.StcEventService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object Phase1TestGroupController extends Phase1TestGroupController {
  override val phase1Repository: Phase1TestMongoRepository = phase1TestRepository
  override val phase1Repository2: Phase1TestMongoRepository2 = phase1TestRepository2
  override val phase1TestExtensionService = OnlineTestExtensionService
  val eventService: StcEventService = StcEventService
}

trait Phase1TestGroupController extends BaseController {

  val phase1Repository: Phase1TestRepository
  val phase1Repository2: Phase1TestRepository2
  val phase1TestExtensionService: OnlineTestExtensionService
  val eventService: StcEventService

  def getTests(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    phase1Repository2.getTestGroup(applicationId).map { maybeTestProfile =>
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
