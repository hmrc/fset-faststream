/*
 * Copyright 2017 HM Revenue & Customs
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

package model.persisted

import model.persisted.{ ApplicationForDiversityReport, CivilServiceExperienceDetailsForDiversityReport }
import model.{ ApplicationRoute, SchemeType }

object ApplicationForDiversityReportExamples {

  val Example1 =
    ApplicationForDiversityReport("appId5", "userId10", ApplicationRoute.Faststream, Some("phase1_tests_completed"),
      List(SchemeType.DiplomaticService, SchemeType.Commercial), Some("No"), Some(false), Some("No"), Some("No"),
      Some(CivilServiceExperienceDetailsForDiversityReport(Some("Yes"), Some("Yes"), Some("No"), Some("No"), Some("No"),
        Some(""))))

  val Example2 =
    ApplicationForDiversityReport("appId6", "userId11", ApplicationRoute.Faststream, Some("submitted"),
      List(SchemeType.DiplomaticServiceEconomics, SchemeType.Commercial, SchemeType.GovernmentCommunicationService,
        SchemeType.European), Some("Yes"), Some(true), Some("Yes"), Some("No"),
      Some(CivilServiceExperienceDetailsForDiversityReport(Some("Yes"), Some("Yes"), Some("No"), Some("Yes"), Some("No"),
        Some("fastPass-101"))))
}
