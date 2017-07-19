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

package model.assessmentscores

import model.UniqueIdentifier


object AssessmentScoresAllExercisesExamples {
  val AppId1 = UniqueIdentifier.randomUniqueIdentifier
  val AppId2 = UniqueIdentifier.randomUniqueIdentifier

  val Example1 = AssessmentScoresAllExercises(
    AppId1,
    Some(AssessmentScoresExerciseExamples.Example1),
    Some(AssessmentScoresExerciseExamples.Example2),
    Some(AssessmentScoresExerciseExamples.Example3)
  )
  val Example2 = AssessmentScoresAllExercises(
    AppId2,
    Some(AssessmentScoresExerciseExamples.Example3),
    Some(AssessmentScoresExerciseExamples.Example1),
    Some(AssessmentScoresExerciseExamples.Example2)
  )
}
