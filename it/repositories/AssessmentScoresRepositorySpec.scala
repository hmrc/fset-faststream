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

import config.MicroserviceAppConfig

import java.util.UUID
import factories.{ITDateTimeFactoryMock, UUIDFactory}
import model.UniqueIdentifier
import model.assessmentscores._
import model.command.AssessmentScoresCommands.AssessmentScoresSectionType
import model.fsacscores.AssessmentScoresFinalFeedbackExamples
import org.joda.time.DateTimeZone
import org.mockito.Mockito.when
import repositories.application.PreviousYearCandidatesDetailsMongoRepository
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
      val indexes = indexDetails(repository).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "applicationId_1", keys = Seq(("applicationId", "Ascending")), unique = true)
        )
    }
  }

  "save" should {
    "create new assessment scores when they do not exist" in new TestFixture  {
      getRepository.save(Scores).futureValue
      val result = repository.find(ApplicationId).futureValue
      result mustBe Some(Scores)
    }

    "override existing assessment scores when they exist" in new TestFixture  {
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

    // This tests that Double values with no fraction part like 2.0 are not stored in Mongo as Int32 by HMRC Codecs.toBson
    // but as Doubles and are being read back correctly as Doubles
    // This verifies that legacyNumbers = true is set otherwise the numeric data gets stored as Int32 instead of Double
    // eg Some(2.0) is stored as 2 not 2.0
    // Note that the Codec seems to read from Mongo Int32 and successfully deserialize into a Double in the case class which
    // is why we have this test here. It is only when you read from Mongo not using the Codec that the problem occurs, which
    // is how the streaming reports do it
    "successfully read the scores from mongo as double values after saving" in new TestFixture  {
      getRepository.save(ScoresNoFractions).futureValue
      val result = repository.find(ApplicationId).futureValue
      result mustBe Some(ScoresNoFractions)

      val csvExtract = collectionName match {
        case CollectionNames.ASSESSOR_ASSESSMENT_SCORES => reportRepo.findAssessorAssessmentScores(Seq(ApplicationId.toString)).futureValue
        case CollectionNames.REVIEWER_ASSESSMENT_SCORES => reportRepo.findReviewerAssessmentScores(Seq(ApplicationId.toString)).futureValue
      }
      csvExtract.records.size mustBe 1
      val dataElement = csvExtract.elementAt(ApplicationId.toString, 12)
      dataElement mustBe Some("\"2.0\"")
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
      val ExpectedScores = AssessmentScoresAllExercises(
        ApplicationId, writtenExercise = None, teamExercise = None, Some(ExerciseScoresExpected), finalFeedback = None
      )
      result mustBe Some(ExpectedScores)
    }

    // This tests that Double values with no fraction part like 2.0 are not stored in Mongo as Int32 by HMRC Codecs.toBson
    // as we do in the save section above
    "successfully read the scores from mongo as double values after saving" in new TestFixture  {
      val ExerciseScoresToSave = ExerciseScoresNoFractions.copy(version = None, submittedDate = None)

      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.leadershipExercise, ExerciseScoresToSave,
        Some(NewVersion)).futureValue

      val result = repository.find(ApplicationId).futureValue
      val ExerciseScoresExpected = ExerciseScoresNoFractions.copy(submittedDate = None, version = Some(NewVersion))
      val ExpectedScores = AssessmentScoresAllExercises(
        ApplicationId, writtenExercise = None, teamExercise = None, Some(ExerciseScoresExpected), finalFeedback = None
      )
      result mustBe Some(ExpectedScores)

      val csvExtract = collectionName match {
        case CollectionNames.ASSESSOR_ASSESSMENT_SCORES => reportRepo.findAssessorAssessmentScores(Seq(ApplicationId.toString)).futureValue
        case CollectionNames.REVIEWER_ASSESSMENT_SCORES => reportRepo.findReviewerAssessmentScores(Seq(ApplicationId.toString)).futureValue
      }
      csvExtract.records.size mustBe 1
      val dataElement = csvExtract.elementAt(ApplicationId.toString, 13)
      dataElement mustBe Some("\"2.0\"")
    }

    "update existing assessment scores with analysis exercise" +
      "when assessment scores contains leadership exercise" in new TestFixture {
      val ExerciseScoresToSave = ExerciseScores.copy(version = None, submittedDate = None)
      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.leadershipExercise, ExerciseScoresToSave,
        Some(NewVersion)).futureValue

      val ExerciseScoresToSave2 = ExerciseScores2.copy(version = None, submittedDate = None)
      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.writtenExercise, ExerciseScoresToSave2,
        Some(NewVersion2)).futureValue

      val result = repository.find(ApplicationId).futureValue
      val ExerciseScoresExpected = ExerciseScores.copy(submittedDate = None, version = Some(NewVersion))
      val ExerciseScores2Expected = ExerciseScores2.copy(submittedDate = None, version = Some(NewVersion2))
      val ExpectedScores = AssessmentScoresAllExercises(ApplicationId, Some(ExerciseScores2Expected), teamExercise = None,
        Some(ExerciseScoresExpected), finalFeedback = None)
      result mustBe Some(ExpectedScores)
    }

    "update analysis exercise in existing assessment scores " +
      "when assessment scores with analysis exercise was saved before by same user" in new TestFixture {
      val ExerciseScoresToSave = ExerciseScores.copy(version = None, submittedDate = None, updatedBy = UpdatedBy)
      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.writtenExercise, ExerciseScoresToSave,
        Some(NewVersion)).futureValue

      val ExerciseScoresToSave2 = ExerciseScores2.copy(version = Some(NewVersion), submittedDate = None, updatedBy = UpdatedBy)
        repository.saveExercise(ApplicationId, AssessmentScoresSectionType.writtenExercise, ExerciseScoresToSave2,
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
      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.writtenExercise, ExerciseScoresToSave,
        Some(NewVersion)).futureValue

      val ExerciseScoresToSave2 = ExerciseScores2.copy(version = None, submittedDate = None, updatedBy = UpdatedBy2)

      try {
        repository.saveExercise(ApplicationId, AssessmentScoresSectionType.writtenExercise, ExerciseScoresToSave2,
          Some(NewVersion2)).failed.futureValue
      } catch {
        case ex: Exception => ex.getClass mustBe classOf[model.Exceptions.NotFoundException]
      }
    }

    "throw Not Found Exception " +
      "when the exercise to be updated is an old version of the existing one" in new TestFixture {
      val ExerciseScoresToSave = ExerciseScores.copy(version = None, submittedDate = None, updatedBy = UpdatedBy)
      repository.saveExercise(ApplicationId, AssessmentScoresSectionType.writtenExercise, ExerciseScoresToSave,
        Some(NewVersion)).futureValue

      val ExerciseScoresToSave2 = ExerciseScores2.copy(version = Some(OldVersion), submittedDate = None, updatedBy = UpdatedBy)

      try {
        repository.saveExercise(ApplicationId, AssessmentScoresSectionType.writtenExercise, ExerciseScoresToSave2,
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
      val ExpectedScores = AssessmentScoresAllExercises(
        ApplicationId, writtenExercise = None, teamExercise = None, leadershipExercise = None, Some(FinalFeedbackExpected)
      )
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

  "findAccepted" should {
    "return None when there are no assessment scores" in new TestFixture  {
      val nonExistantApplicationId = UniqueIdentifier.randomUniqueIdentifier

      if (collectionName == CollectionNames.ASSESSOR_ASSESSMENT_SCORES) {
        // Not applicable for an assessor
        try {
          repository.findAccepted(nonExistantApplicationId).failed.futureValue
        } catch {
          case ex: Exception => ex.getClass mustBe classOf[UnsupportedOperationException]
        }
      } else {
        // Only applicable for a reviewer
        val result = repository.findAccepted(nonExistantApplicationId).futureValue
        result mustBe None
      }
    }

    "return all assessment scores when there are some" in new TestFixture {
      val finalFeedbackToSave = FinalFeedback.copy(version = None, acceptedDate = LocalTime)
      repository.saveFinalFeedback(ApplicationId, finalFeedbackToSave, Some(NewVersion)).futureValue

      if (collectionName == CollectionNames.ASSESSOR_ASSESSMENT_SCORES) {
        // Not applicable for an assessor
        try {
          repository.findAccepted(ApplicationId).failed.futureValue
        } catch {
          case ex: Exception => ex.getClass mustBe classOf[UnsupportedOperationException]
        }
      } else {
        // Only applicable for a reviewer
        val result = repository.findAccepted(ApplicationId).futureValue

        val FinalFeedbackExpected = FinalFeedback.copy(acceptedDate = LocalTime.withZone(DateTimeZone.UTC), version = Some(NewVersion))
        val ExpectedScores = AssessmentScoresAllExercises(
          ApplicationId, writtenExercise = None, teamExercise = None, leadershipExercise = None, finalFeedback = Some(FinalFeedbackExpected)
        )
        result mustBe Some(ExpectedScores)
      }
    }
  }

  "findAllByIds" should {
    "return empty list when there are no assessment scores" in new TestFixture  {
      val result = repository.findAllByIds(Seq(UniqueIdentifier.randomUniqueIdentifier.toString)).futureValue
      result mustBe Nil
    }

    "return all assessment scores when there are some" in new TestFixture {
      repository.save(Scores).futureValue
      repository.save(Scores2).futureValue

      val result = repository.findAllByIds(Seq(ApplicationId.toString, ApplicationId2.toString)).futureValue
      result must contain theSameElementsAs Seq(Scores, Scores2)
    }
  }

  "resetExercise" should {
    "reset the expected exercises" in new TestFixture  {
      repository.save(Scores3).futureValue

      val resultBeforeReset = repository.find(ApplicationId).futureValue
      resultBeforeReset mustBe Some(Scores3)

      val exercisesToRemove = List(
        AssessmentScoresSectionType.writtenExercise.toString,
        AssessmentScoresSectionType.teamExercise.toString,
        AssessmentScoresSectionType.leadershipExercise.toString,
        AssessmentScoresSectionType.finalFeedback.toString
      )
      repository.resetExercise(ApplicationId, exercisesToRemove).futureValue

      val resultAfterReset = repository.find(ApplicationId).futureValue
      resultAfterReset mustBe Some(AssessmentScoresAllExercisesExamples.NoExercises.copy(applicationId = ApplicationId))
    }
  }

  trait TestFixture {
    val ApplicationId = UniqueIdentifier(UUID.fromString(UUIDFactory.generateUUID()))
    val ApplicationId2 = UniqueIdentifier(UUID.fromString(UUIDFactory.generateUUID()))
    val Scores = AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExercise.copy(applicationId = ApplicationId)
    val ScoresNoFractions = AssessmentScoresAllExercisesExamples.AssessorOnlyLeadershipExerciseNoFractions.copy(applicationId = ApplicationId)
    val Scores2 = AssessmentScoresAllExercisesExamples.AssessorOnlyGroupExercise.copy(applicationId = ApplicationId2)
    val Scores3 = AssessmentScoresAllExercisesExamples.AllExercises.copy(applicationId = ApplicationId)
    val ExerciseScores = AssessmentScoresExerciseExamples.Example3
    val ExerciseScoresNoFractions = AssessmentScoresExerciseExamples.ExampleNoFractions
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

    val schemeRepositoryMock = mock[SchemeRepository]
    when(schemeRepositoryMock.schemes).thenReturn(Seq())

    val appConfigMock = mock[MicroserviceAppConfig]

    val reportRepo = new PreviousYearCandidatesDetailsMongoRepository(
      ITDateTimeFactoryMock,
      appConfigMock,
      schemeRepositoryMock,
      mongo
    )
  }
}
