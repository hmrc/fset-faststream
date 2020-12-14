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

package controllers.metrics

import javax.inject.{ Inject, Singleton }
import model.ApplicationStatus
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.application.GeneralApplicationRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.collection.immutable.SortedMap
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class MetricsController @Inject() (cc: ControllerComponents, applicationRepo: GeneralApplicationRepository) extends BackendController(cc) {

  def progressStatusCounts = Action.async {
    for {
      allApplications <- applicationRepo.countLong
      createdCount <- applicationRepo.countByStatus(ApplicationStatus.CREATED)
      list <- applicationRepo.getLatestProgressStatuses
    } yield {
      val listWithCounts = SortedMap[String, Long]() ++ list.groupBy(identity).mapValues(_.size.toLong)
      Ok(Json.toJson(
        listWithCounts ++
          Map("TOTAL_APPLICATION_COUNT" -> allApplications.toLong) ++
          Map("CREATED" -> createdCount)
      ))
    }
  }
}
