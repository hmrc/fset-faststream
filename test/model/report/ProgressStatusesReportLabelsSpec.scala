/*
 * Copyright 2019 HM Revenue & Customs
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

import model.command.{ Phase3ProgressResponse, ProgressResponse, ProgressResponseExamples }
import testkit.UnitSpec

class ProgressStatusesReportLabelsSpec extends UnitSpec {

  import ProgressStatusesReportLabelsSpec._

  "a registered application" should {
    "return registered" in {
      val status = ProgressStatusesReportLabels.progressStatusNameInReports(new ProgressResponse("id", false, false,
        false, false, false, Nil, false, false))
      status mustBe ("registered")
    }
  }

  "a withdrawn application" should {
    "return withdrawn" in {
      ProgressStatusesReportLabels.progressStatusNameInReports(progressResponse) mustBe "withdrawn"
    }

    "return withdrawn when all other progresses are set" in {
      ProgressStatusesReportLabels.progressStatusNameInReports(completeProgressResponse) mustBe "withdrawn"
    }

    "return withdrawn when one or more test are expired" in {
      ProgressStatusesReportLabels.progressStatusNameInReports(expiredProgressResponse) mustBe "withdrawn"
    }
  }

  "a submitted application" should {
    "return submitted" in {
      val customProgress = progressResponse.copy(withdrawn = false)
      ProgressStatusesReportLabels.progressStatusNameInReports(customProgress) mustBe "submitted"
    }
  }

  "a test expired application" should {
    "return phase3_tests_expired" in {
      val customProgress = expiredProgressResponse.copy(withdrawn = false)
      ProgressStatusesReportLabels.progressStatusNameInReports(customProgress) mustBe "phase3_tests_expired"
    }
  }

  "a previewed application" should {
    "return previewed" in {
      val customProgress = ProgressResponseExamples.InPreview
      ProgressStatusesReportLabels.progressStatusNameInReports(customProgress) mustBe "preview_completed"
    }
  }

  "an application in partner graduate programmes" should {
    "Return partner_graduate_programmes_completed" in {
      val customProgress = ProgressResponseExamples.InPartnerGraduateProgrammes
      ProgressStatusesReportLabels.progressStatusNameInReports(customProgress) mustBe "partner_graduate_programmes_completed"
    }
  }

  "an application in scheme preferences" should {
    "return scheme_preferences_completed" in {
      val customProgress = ProgressResponseExamples.InSchemePreferences
      ProgressStatusesReportLabels.progressStatusNameInReports(customProgress) mustBe "scheme_preferences_completed"
    }
  }

  "an application in personal details" should {
    "return personal_details_completed" in {
      val customProgress = ProgressResponseExamples.InPersonalDetails
      ProgressStatusesReportLabels.progressStatusNameInReports(customProgress) mustBe "personal_details_completed"
    }

    "return personal_details_completed when sections are not completed" in {
      val customProgress = ProgressResponseExamples.InPersonalDetails
      ProgressStatusesReportLabels.progressStatusNameInReports(customProgress) mustBe "personal_details_completed"
    }
  }
}

object ProgressStatusesReportLabelsSpec {

  val progressResponse = ProgressResponse("1", personalDetails = true, schemePreferences = true,
    partnerGraduateProgrammes = true, assistanceDetails = true, preview = true,
    List("start_questionnaire", "diversity_questionnaire", "education_questionnaire", "occupation_questionnaire"),
    submitted = true, withdrawn = true
  )

  val completeProgressResponse = ProgressResponse("1", personalDetails = true, schemePreferences = true,
    partnerGraduateProgrammes = true, assistanceDetails = true, preview = true,
    List("start_questionnaire", "diversity_questionnaire", "education_questionnaire", "occupation_questionnaire"),
    submitted = true, withdrawn = true
  )

  val expiredProgressResponse = progressResponse.copy(phase3ProgressResponse = Phase3ProgressResponse(
    phase3TestsInvited = true,
    phase3TestsFirstReminder = true,
    phase3TestsSecondReminder = true,
    phase3TestsStarted = true,
    phase3TestsCompleted = true,
    phase3TestsExpired = true,
    phase3TestsResultsReceived = true
  ))
}
