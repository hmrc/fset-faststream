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

package model.report

import model.ApplicationRoute._
import model.SchemeId
import model.assessmentscores.AssessmentScoresAllExercises
import model.persisted.fsb.ScoresAndFeedback
import model.persisted.{ ApplicationForOnlineTestPassMarkReport, SchemeEvaluationResult }
import play.api.libs.json.{ Json, OFormat }

case class ApplicationForOnlineTestPassMarkReportItem(
                                                       applicationId: String,
                                                       progress: String,
                                                       applicationRoute: ApplicationRoute,
                                                       sdipDiversity: Option[Boolean],
                                                       schemes: List[SchemeId],
                                                       disability: Option[String],
                                                       gis: Option[Boolean],
                                                       onlineAdjustments: Option[String],
                                                       assessmentCentreAdjustments: Option[String],
                                                       testResults: TestResultsForOnlineTestPassMarkReportItem,
                                                       currentSchemeStatus: List[SchemeEvaluationResult])

object ApplicationForOnlineTestPassMarkReportItem {
  implicit val applicationForOnlineTestReportItemFormat: OFormat[ApplicationForOnlineTestPassMarkReportItem] =
    Json.format[ApplicationForOnlineTestPassMarkReportItem]

  def apply(a: ApplicationForOnlineTestPassMarkReport,
           fsacResults: Option[AssessmentScoresAllExercises],
           overallScoreOpt: Option[Double],
           siftResults: Option[SiftPhaseReportItem],
           fsbScoresAndFeedback: Option[ScoresAndFeedback]): ApplicationForOnlineTestPassMarkReportItem = {

    ApplicationForOnlineTestPassMarkReportItem(
      applicationId = a.applicationId,
      progress = a.progress,
      applicationRoute = a.applicationRoute,
      sdipDiversity = a.sdipDiversity,
      schemes = a.schemes,
      disability = a.disability,
      gis = a.gis,
      onlineAdjustments = a.onlineAdjustments,
      assessmentCentreAdjustments = a.assessmentCentreAdjustments,
      testResults = a.testResults.copy(
        fsac = fsacResults.map(_.toExchange), overallFsacScore = overallScoreOpt, sift = siftResults, fsb = fsbScoresAndFeedback
      ),
      currentSchemeStatus = a.currentSchemeStatus
    )
  }
}
