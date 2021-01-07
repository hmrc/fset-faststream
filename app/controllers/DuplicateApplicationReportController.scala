/*
 * Copyright 2021 HM Revenue & Customs
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
import model.report.DuplicateApplicationsReportItem
import play.api.libs.json.Json
import play.api.mvc.{ Action, ControllerComponents }
import services.reporting.{ DuplicateApplicationGroup, DuplicateDetectionService }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class DuplicateApplicationReportController @Inject() (cc: ControllerComponents,
                                                      duplicateDetectionService: DuplicateDetectionService) extends BackendController(cc) {

  def findPotentialDuplicates = Action.async { implicit request =>
    duplicateDetectionService.findAll.map { potentialDuplications =>
      val result = potentialDuplications.zipWithIndex.flatMap { case (dup, matchGroup) =>
        toReportItem(dup, matchGroup)
      }
      Ok(Json.toJson(result))
    }
  }

  private def toReportItem(source: DuplicateApplicationGroup, matchGroup: Int) = {
    source.candidates.map { c =>
      DuplicateApplicationsReportItem(c.firstName, c.lastName, c.email, c.latestProgressStatus, source.matchType,
        matchGroup, c.applicationRoute)
    }
  }
}
