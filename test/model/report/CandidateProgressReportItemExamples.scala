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

package model.report

import model.{ ApplicationRoute, SchemeId }

object CandidateProgressReportItemExamples {
  lazy val SdipCandidate = CandidateProgressReportItem(userId = "459b5e72-e004-48ff-9f00-adbddf59d9c4",
    applicationId = "a665043b-8317-4d28-bdf6-086859ac17ff",
    Some("submitted"), List(SchemeId("Sdip")), disability = Some("Yes"), onlineAdjustments = Some("No"),
    assessmentCentreAdjustments = Some("Yes"), phoneAdjustments = Some("Yes"),
    gis = Some("No"), civilServant = Some("Yes"), edip = Some("Yes"), sdip = Some("No"),
    otherInternship = Some("Yes"), fastPassCertificate = Some("No"), assessmentCentre = None, ApplicationRoute.Sdip)

  lazy val FaststreamCandidate = CandidateProgressReportItem(userId = "459b5e72-e004-48ff-9f00-adbddf59d9c4",
    applicationId = "a665043b-8317-4d28-bdf6-086859ac17ff",
    Some("submitted"), List(SchemeId("Commercial")), disability = Some("Yes"), onlineAdjustments = Some("No"),
    assessmentCentreAdjustments = Some("Yes"), phoneAdjustments = Some("Yes"),
    gis = Some("No"), civilServant = Some("Yes"), edip = Some("Yes"), sdip = Some("Yes"),
    otherInternship = Some("Yes"), fastPassCertificate = Some("1234567"), assessmentCentre = None, ApplicationRoute.Faststream)
}
