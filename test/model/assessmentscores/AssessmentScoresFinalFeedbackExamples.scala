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

package model.fsacscores

import model.UniqueIdentifier
import model.assessmentscores.AssessmentScoresFinalFeedback

import java.time.temporal.ChronoUnit
import java.time.{OffsetDateTime, ZoneId}

object AssessmentScoresFinalFeedbackExamples {
  // Create the date truncated to milliseconds as that is the precision of the date stored in mongo
  // and the comparison will work when we fetch the date back from the db and check it
  val Example1 = AssessmentScoresFinalFeedback("feedback1", UniqueIdentifier.randomUniqueIdentifier,
    OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS))
  val Example2 = AssessmentScoresFinalFeedback("feedback2", UniqueIdentifier.randomUniqueIdentifier,
    OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS))
}
