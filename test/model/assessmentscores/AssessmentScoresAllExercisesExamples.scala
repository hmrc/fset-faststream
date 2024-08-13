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

package model.assessmentscores

import model.UniqueIdentifier
import model.fsacscores.AssessmentScoresFinalFeedbackExamples

object AssessmentScoresAllExercisesExamples {
  val AppId1 = UniqueIdentifier.randomUniqueIdentifier
  val AppId2 = UniqueIdentifier.randomUniqueIdentifier
  val AppId3 = UniqueIdentifier.randomUniqueIdentifier
  val AppId4 = UniqueIdentifier.randomUniqueIdentifier
  val AppId5 = UniqueIdentifier.randomUniqueIdentifier
  val AppId6 = UniqueIdentifier.randomUniqueIdentifier

  val AssessorOnlyExercise1 = AssessmentScoresAllExercises(
    AppId1,
    Some(AssessmentScoresExerciseExamples.Example1),
    exercise2 = None,
    exercise3 = None
  )

  val AssessorOnlyExercise2 = AssessmentScoresAllExercises(
    AppId2,
    exercise1 = None,
    Some(AssessmentScoresExerciseExamples.Example2),
    exercise3 = None
  )

  val AssessorOnlyExercise3 = AssessmentScoresAllExercises(
    AppId3,
    exercise1 = None,
    exercise2 = None,
    Some(AssessmentScoresExerciseExamples.Example3)
  )

  val AssessorOnlyExercise3NoFractions = AssessmentScoresAllExercises(
    AppId3,
    exercise1 = None,
    exercise2 = None,
    Some(AssessmentScoresExerciseExamples.ExampleNoFractions)
  )

  val AssessorAllButExercise1 = AssessmentScoresAllExercises(
    AppId4,
    exercise1 = None,
    Some(AssessmentScoresExerciseExamples.Example2),
    Some(AssessmentScoresExerciseExamples.Example3),
    Some(AssessmentScoresFinalFeedbackExamples.Example1)
  )

  val NoExercises = AssessmentScoresAllExercises(
    AppId5,
    exercise1 = None,
    exercise2 = None,
    exercise3 = None,
    finalFeedback = None
  )

  val AllExercises = AssessmentScoresAllExercises(
    AppId6,
    Some(AssessmentScoresExerciseExamples.Example1),
    Some(AssessmentScoresExerciseExamples.Example2),
    Some(AssessmentScoresExerciseExamples.Example3),
    Some(AssessmentScoresFinalFeedbackExamples.Example1)
  )

  val AllExercisesButFinalFeedback = AssessmentScoresAllExercises(
    AppId6,
    Some(AssessmentScoresExerciseExamples.Example1),
    Some(AssessmentScoresExerciseExamples.Example2),
    Some(AssessmentScoresExerciseExamples.Example3),
    finalFeedback = None
  )
}
