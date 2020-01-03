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

package model.report

import model.ApplicationRoute._
import model.SchemeId
import play.api.libs.json.Json
import model.persisted.{ApplicationForDiversityReport, SchemeEvaluationResult}

case class ApplicationForDiversityReportItem(progress: Option[String],
                                             applicationRoute: ApplicationRoute,
                                             schemes: List[SchemeId],
                                             disability: Option[String],
                                             gis: Option[Boolean],
                                             onlineAdjustments: Option[String],
                                             assessmentCentreAdjustments: Option[String],
                                             civilServiceExperiencesDetails: Option[CivilServiceExperienceDetailsReportItem],
                                             currentSchemeStatus: List[SchemeEvaluationResult])

case class DiversityReportItem(application: ApplicationForDiversityReportItem,
                               questionnaire: Option[QuestionnaireReportItem],
                               media: Option[MediaReportItem])

object ApplicationForDiversityReportItem {
  implicit val applicationForDiversityReportItemFormat = Json.format[ApplicationForDiversityReportItem]

  def create(a: ApplicationForDiversityReport): ApplicationForDiversityReportItem = {
    ApplicationForDiversityReportItem(progress = a.progress,
      applicationRoute = a.applicationRoute,
      schemes = a.schemes,
      disability = a.disability,
      gis = a.gis,
      onlineAdjustments = a.onlineAdjustments,
      assessmentCentreAdjustments = a.assessmentCentreAdjustments,
      civilServiceExperiencesDetails = a.civilServiceExperiencesDetails.map { c =>
        CivilServiceExperienceDetailsReportItem.create(c)
      },
      currentSchemeStatus = a.currentSchemeStatus
    )
  }
}

object DiversityReportItem {
  implicit val diversityReportFormat = Json.format[DiversityReportItem]
}
