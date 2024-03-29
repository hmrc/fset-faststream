/*
 * Copyright 2023 HM Revenue & Customs
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

package model

import play.api.libs.json.{ Json, OFormat }

object OnlineTestCommands {

  case class OnlineTestApplication(applicationId: String,
                                   applicationStatus: String,
                                   userId: String,
                                   testAccountId: String,
                                   guaranteedInterview: Boolean,
                                   needsAtVenueAdjustments: Boolean,
                                   preferredName: String,
                                   lastName: String,
                                   eTrayAdjustments: Option[AdjustmentDetail],
                                   videoInterviewAdjustments: Option[AdjustmentDetail]) {
    def isInvigilatedETray = eTrayAdjustments.exists(_.invigilatedInfo.isDefined)
    def isInvigilatedVideo = videoInterviewAdjustments.exists(_.invigilatedInfo.isDefined)
  }

  case class OnlineTestReportAvailability(reportId: Int, available: Boolean)

  case class OnlineTestReport(xml: Option[String])

  case class TimeAdjustmentsOnlineTestApplication(etrayTimeAdjustmentPercentage: Int,
                                                  videoInterviewTimeAdjustmentPercentage: Int)

  case class PsiTestResult(status: String, tScore: Double, raw: Double)

  object PsiTestResult {
    implicit val psiTestResultFormat: OFormat[PsiTestResult] = Json.format[PsiTestResult]
  }

  object Implicits {
    implicit val TimeAdjustmentsOnlineTestApplicationFormats: OFormat[TimeAdjustmentsOnlineTestApplication] =
      Json.format[TimeAdjustmentsOnlineTestApplication]
    implicit val ApplicationForOnlineTestingFormats: OFormat[OnlineTestApplication] = Json.format[OnlineTestApplication]
    implicit val OnlineTestReportIdMRAFormats: OFormat[OnlineTestReportAvailability] = Json.format[OnlineTestReportAvailability]
  }
}
