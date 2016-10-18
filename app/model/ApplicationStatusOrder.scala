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

package model

import model.command.ProgressResponse
import model.ProgressStatuses._

object ApplicationStatusOrder {

  def getStatus(progress: Option[ProgressResponse]): String = progress match {
    case Some(p) => getStatus(p)
    case None => REGISTERED.key
  }

  def getStatus(progress: ProgressResponse): String = {

    val map = statusMaps(progress)
    require(map.length == ProgressStatuses.allStatuses.length, "The status map must have the same number of statuses")

    val activeProgressStatuses = map.collect {
      case (true, ps) => ps
    }

    activeProgressStatuses.sorted.reverse.head.key
  }

  def isNonSubmittedStatus(progress: ProgressResponse): Boolean = {
    val isNotSubmitted = !progress.submitted
    val isNotWithdrawn = !progress.withdrawn
    isNotWithdrawn && isNotSubmitted
  }

  def statusMaps(progress: ProgressResponse) = Seq[(Boolean, ProgressStatus)](
    (true, REGISTERED),
    (progress.personalDetails, PERSONAL_DETAILS_COMPLETED),
    (progress.schemePreferences, SCHEME_PREFERENCES_COMPLETED),
    (progress.partnerGraduateProgrammes, IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES_COMPLETED),
    (progress.assistanceDetails, IN_PROGRESS_ASSISTANCE_DETAILS_COMPLETED),
    (progress.questionnaire.contains("start_questionnaire"), START_DIVERSITY_QUESTIONNAIRE_COMPLETED),
    (progress.questionnaire.contains("diversity_questionnaire"), DIVERSITY_QUESTIONNAIRE_COMPLETED),
    (progress.questionnaire.contains("education_questionnaire"), EDUCATION_QUESTIONS_COMPLETED),
    (progress.questionnaire.contains("occupation_questionnaire"), OCCUPATION_QUESTIONS_COMPLETED),
    (progress.preview, PREVIEW),
    (progress.submitted, SUBMITTED),
    (progress.phase1ProgressResponse.phase1TestsInvited, PHASE1_TESTS_INVITED),
    (progress.phase1ProgressResponse.phase1TestsFirstRemainder, PHASE1_TESTS_FIRST_REMINDER),
    (progress.phase1ProgressResponse.phase1TestsSecondRemainder, PHASE1_TESTS_SECOND_REMINDER),
    (progress.phase1ProgressResponse.phase1TestsStarted, PHASE1_TESTS_STARTED),
    (progress.phase1ProgressResponse.phase1TestsCompleted, PHASE1_TESTS_COMPLETED),
    (progress.phase1ProgressResponse.phase1TestsExpired, PHASE1_TESTS_EXPIRED),
    (progress.phase1ProgressResponse.phase1TestsResultsReady, PHASE1_TESTS_RESULTS_READY),
    (progress.phase1ProgressResponse.phase1TestsResultsReceived, PHASE1_TESTS_RESULTS_RECEIVED),
    (progress.phase1ProgressResponse.phase1TestsPassed, PHASE1_TESTS_PASSED),
    (progress.phase1ProgressResponse.phase1TestsFailed, PHASE1_TESTS_FAILED),
    (progress.phase2ProgressResponse.phase2TestsInvited, PHASE2_TESTS_INVITED),
    (progress.phase2ProgressResponse.phase2TestsFirstRemainder, PHASE2_TESTS_FIRST_REMINDER),
    (progress.phase2ProgressResponse.phase2TestsSecondRemainder, PHASE2_TESTS_SECOND_REMINDER),
    (progress.phase2ProgressResponse.phase2TestsStarted, PHASE2_TESTS_STARTED),
    (progress.phase2ProgressResponse.phase2TestsCompleted, PHASE2_TESTS_COMPLETED),
    (progress.phase2ProgressResponse.phase2TestsExpired, PHASE2_TESTS_EXPIRED),
    (progress.phase2ProgressResponse.phase2TestsResultsReady, PHASE2_TESTS_RESULTS_READY),
    (progress.phase2ProgressResponse.phase2TestsResultsReceived, PHASE2_TESTS_RESULTS_RECEIVED),
    (progress.phase2ProgressResponse.phase2TestsPassed, PHASE2_TESTS_PASSED),
    (progress.phase2ProgressResponse.phase2TestsFailed, PHASE2_TESTS_FAILED),
    (progress.phase3ProgressResponse.phase3TestsInvited, PHASE3_TESTS_INVITED),
    (progress.phase3ProgressResponse.phase3TestsStarted, PHASE3_TESTS_STARTED),
    (progress.phase3ProgressResponse.phase3TestsCompleted, PHASE3_TESTS_COMPLETED),
    (progress.withdrawn, WITHDRAWN)
  )
}
