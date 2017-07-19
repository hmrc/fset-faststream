package model.fsacscores

import model.UniqueIdentifier


object FSACAllExercisesScoresAndFeedbackExamples {
  val AppId1 = UniqueIdentifier.randomUniqueIdentifier
  val AppId2 = UniqueIdentifier.randomUniqueIdentifier

  val Example1 = FSACAllExercisesScoresAndFeedback(
    AppId1,
    Some(FSACExerciseScoresAndFeedbackExamples.Example1),
    Some(FSACExerciseScoresAndFeedbackExamples.Example2),
    Some(FSACExerciseScoresAndFeedbackExamples.Example3)
  )
  val Example2 = FSACAllExercisesScoresAndFeedback(
    AppId2,
    Some(FSACExerciseScoresAndFeedbackExamples.Example3),
    Some(FSACExerciseScoresAndFeedbackExamples.Example1),
    Some(FSACExerciseScoresAndFeedbackExamples.Example2)
  )
}
