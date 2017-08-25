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

package command

import model.ApplicationStatus
import model.command.ApplicationForSift
import model.persisted.SchemeEvaluationResult

object ApplicationForSiftExamples {

  def phase3TestNotified(applicationId: String) = ApplicationForSift(applicationId, ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
    currentSchemeStatus = Seq(SchemeEvaluationResult("EDIP", "Pass")))
}
