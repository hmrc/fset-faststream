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

package repositories

import java.util.UUID

import factories.{ ITDateTimeFactoryMock, UUIDFactory }
import model.UniqueIdentifier
import model.assessmentscores._
import model.command.AssessmentScoresCommands.AssessmentScoresSectionType
import model.fsacscores.AssessmentScoresFinalFeedbackExamples
import org.joda.time.DateTimeZone
import reactivemongo.api.indexes.IndexType.Ascending
import testkit.MongoRepositorySpec

class AssessorAssessmentScoresRepositorySpec extends AssessmentScoresRepositorySpec {
  override val collectionName = CollectionNames.ASSESSOR_ASSESSMENT_SCORES
  override def getRepository = new AssessorAssessmentScoresMongoRepository(mongo, UUIDFactory)
}

class ReviewerAssessmentScoresRepositorySpec extends AssessmentScoresRepositorySpec {
  override val collectionName = CollectionNames.REVIEWER_ASSESSMENT_SCORES
  override def getRepository = new ReviewerAssessmentScoresMongoRepository(mongo, UUIDFactory)
}

trait AssessmentScoresRepositorySpec extends MongoRepositorySpec {
  def getRepository: AssessmentScoresMongoRepository

  "Assessment Scores Repository" should {
    "create indexes for the repository" in new TestFixture {
      val indexes = indexesWithFields2(repository)
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(key = Seq(("_id", Ascending)), unique = false),
          IndexDetails(key = Seq(("applicationId", Ascending)), unique = true)
        )
    }
  }

  "save" should {
    "create new assessment scores when it does not exist" in new TestFixture  {
      getRepository.save(Scores).futureValue
      val result = repository.find(ApplicationId).futureValue
      result mustBe Some(Scores)
    }

    "override existing assessment scores when it exists" in new TestFixture  {
      repository.save(Scores).futureValue
      val ScoresRead = repository.find(ApplicationId).futureValue

      val ScoresModified =
        Scores.copy(leadershipExercise = Scores.leadershipExercise.map(
          _.copy(communicatingAndInfluencingAverage = Some(3.7192))))
      repository.save(ScoresModified).futureValue
      val ScoresModifiedRead = repository.find(ApplicationId).futureValue

      Some(ScoresModified) mustBe ScoresModifiedRead
      ScoresRead must not be ScoresModifiedRead
    }
  }

  "save exercise" should {
    "create new assessment scores with one exercise " +
      "when assessment scores do not exist" in new TestFixture  {
      val ExerciseScoresToSave = ExerciseScores.copy(version = None, submittedDate = None)

      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.leadershipExercise, ExerciseScoresToSave,
        Some(NewVersion)).futureValue

      val result = repository.find(ApplicationId).futureValue
      val ExerciseScoresExpected = ExerciseScores.copy(submittedDate = None, version = Some(NewVersion))
      val ExpectedScores = AssessmentScoresAllExercises(ApplicationId, None, None, Some(ExerciseScoresExpected), None)
      result mustBe Some(ExpectedScores)
    }

    "update existing assessment scores with analysis exercise" +
      "when assessment scores contains leadership exercise" in new TestFixture {
      val ExerciseScoresToSave = ExerciseScores.copy(version = None, submittedDate = None)
      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.leadershipExercise, ExerciseScoresToSave,
        Some(NewVersion)).futureValue

      val ExerciseScoresToSave2 = ExerciseScores2.copy(version = None, submittedDate = None)
      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.analysisExercise, ExerciseScoresToSave2,
        Some(NewVersion2)).futureValue

      val result = repository.find(ApplicationId).futureValue
      val ExerciseScoresExpected = ExerciseScores.copy(submittedDate = None, version = Some(NewVersion))
      val ExerciseScores2Expected = ExerciseScores2.copy(submittedDate = None, version = Some(NewVersion2))
      val ExpectedScores = AssessmentScoresAllExercises(ApplicationId, Some(ExerciseScores2Expected), None,
        Some(ExerciseScoresExpected), None)
      result mustBe Some(ExpectedScores)
    }

    "update analysis exercise in existing assessment scores " +
      "when assessment scores with analysis exercise was saved before by same user" in new TestFixture {
      val ExerciseScoresToSave = ExerciseScores.copy(version = None, submittedDate = None, updatedBy = UpdatedBy)
      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.analysisExercise, ExerciseScoresToSave,
        Some(NewVersion)).futureValue

      val ExerciseScoresToSave2 = ExerciseScores2.copy(version = Some(NewVersion), submittedDate = None, updatedBy = UpdatedBy)
        repository.saveExercise(ApplicationId, AssessmentScoresSectionType.analysisExercise, ExerciseScoresToSave2,
          Some(NewVersion2)).futureValue

      val result = repository.find(ApplicationId).futureValue
      val ExerciseScores2Expected = ExerciseScores2.copy(submittedDate = None, version = Some(NewVersion2), updatedBy = UpdatedBy)
      val ExpectedScores = AssessmentScoresAllExercises(ApplicationId, Some(ExerciseScores2Expected), None,
        None, None)
      result mustBe Some(ExpectedScores)
    }

    "throw Not Found Exception " +
      "when the exercise to be updated was updated before by another user" in new TestFixture {
      val ExerciseScoresToSave = ExerciseScores.copy(version = None, submittedDate = None, updatedBy = UpdatedBy)
      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.analysisExercise, ExerciseScoresToSave,
        Some(NewVersion)).futureValue

      val ExerciseScoresToSave2 = ExerciseScores2.copy(version = None, submittedDate = None, updatedBy = UpdatedBy2)

      try {
        repository.saveExercise(ApplicationId, AssessmentScoresSectionType.analysisExercise, ExerciseScoresToSave2,
          Some(NewVersion2)).failed.futureValue
      } catch {
        case ex: Exception => ex.getClass mustBe classOf[model.Exceptions.NotFoundException]
      }
    }

    "throw Not Found Exception " +
      "when the exercise to be updated is an old version of the existing one" in new TestFixture {
      val ExerciseScoresToSave = ExerciseScores.copy(version = None, submittedDate = None, updatedBy = UpdatedBy)
      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.analysisExercise, ExerciseScoresToSave,
        Some(NewVersion)).futureValue

      val ExerciseScoresToSave2 = ExerciseScores2.copy(version = Some(OldVersion), submittedDate = None, updatedBy = UpdatedBy)

      try {
        repository.saveExercise(ApplicationId, AssessmentScoresSectionType.analysisExercise, ExerciseScoresToSave2,
          Some(NewVersion2)).failed.futureValue
      } catch {
        case ex: Exception => ex.getClass mustBe classOf[model.Exceptions.NotFoundException]
      }
    }
  }

  // save final feedback will only be called once per assessment scores, once called, it called be called from frontend.
  "save final feedback" should {
    "create new assessment scores with final feedback when it does not exist" in new TestFixture  {
      val FinalFeedbackToSave = FinalFeedback.copy(version = None, acceptedDate = LocalTime)

      repository.saveFinalFeedback(ApplicationId, FinalFeedbackToSave, Some(NewVersion)).futureValue

      val result = repository.find(ApplicationId).futureValue
      val FinalFeedbackExpected = FinalFeedback.copy(acceptedDate = LocalTime.withZone(DateTimeZone.UTC), version = Some(NewVersion))
      val ExpectedScores = AssessmentScoresAllExercises(ApplicationId, None, None, None, Some(FinalFeedbackExpected))
      result mustBe Some(ExpectedScores)
    }

    "throw Not Found Exception " +
      "when the exercise to be updated was updated before by another user" in new TestFixture {
      val FinalFeedbackToSave = FinalFeedback.copy(version = None, acceptedDate = LocalTime)
      repository.saveFinalFeedback(ApplicationId, FinalFeedbackToSave, Some(NewVersion)).futureValue

      val FinalFeedbackToSave2 = FinalFeedback2.copy(version = None, acceptedDate = LocalTime, updatedBy = UpdatedBy2)
      try {
        repository.saveFinalFeedback(ApplicationId, FinalFeedbackToSave2, Some(NewVersion2)).failed.futureValue
      } catch {
        case ex: Exception => ex.getClass mustBe classOf[model.Exceptions.NotFoundException]
      }
    }
  }

  "find" should {
    "return None if assessment scores if they do not exist" in new TestFixture  {
      val NonExistingApplicationId = UniqueIdentifier.randomUniqueIdentifier
      val result = repository.find(NonExistingApplicationId).futureValue
      result mustBe None
    }

    "return assessment scores if they already exists" in new TestFixture  {
      repository.save(Scores).futureValue
      val result = repository.find(ApplicationId).futureValue
      result mustBe Some(Scores)
    }
  }

  "findAll" should {
    "return empty list when there are no assessment scores" in new TestFixture  {
      val result = repository.findAll.futureValue
      result mustBe Nil
    }

    "return all assessment scores when there are some" in new TestFixture {
      repository.save(Scores).futureValue
      repository.save(Scores2).futureValue

      val result = repository.findAll.futureValue

      result must have size 2
      result must contain(Scores)
      result must contain(Scores2)
    }
  }

  trait TestFixture {
    val ApplicationId = UniqueIdentifier(UUID.fromString(UUIDFactory.generateUUID()))
    val ApplicationId2 = UniqueIdentifier(UUID.fromString(UUIDFactory.generateUUID()))
    val Scores = AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise.copy(applicationId = ApplicationId)
    val Scores2 = AssessmentScoresAllExercisesExamples.AssessorOnlyGroupExercise.copy(applicationId = ApplicationId2)
    val ExerciseScores = AssessmentScoresExerciseExamples.Example3
    val ExerciseScores2 = AssessmentScoresExerciseExamples.Example2
    val FinalFeedback = AssessmentScoresFinalFeedbackExamples.Example1
    val FinalFeedback2 = AssessmentScoresFinalFeedbackExamples.Example2

    val LocalTime = ITDateTimeFactoryMock.nowLocalTimeZone
    val LocalDate = ITDateTimeFactoryMock.nowLocalDate
    val OldVersion = UUIDFactory.generateUUID()
    val NewVersion = UUIDFactory.generateUUID()
    val NewVersion2 = UUIDFactory.generateUUID()
    val UpdatedBy = UniqueIdentifier.randomUniqueIdentifier
    val UpdatedBy2 = UniqueIdentifier.randomUniqueIdentifier

    val repository = getRepository
  }
}
