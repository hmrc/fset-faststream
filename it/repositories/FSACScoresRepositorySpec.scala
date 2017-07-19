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

import factories.DateTimeFactory
import model.fsacscores.{ FSACAllExercisesScoresAndFeedback, FSACAllExercisesScoresAndFeedbackExamples, FSACExerciseScoresAndFeedback, FSACExerciseScoresAndFeedbackExamples }
import testkit.MongoRepositorySpec

class FSACScoresRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.FSAC_SCORES

  def repository = new FSACScoresMongoRepository(DateTimeFactory)

  "FSAC Scores Repository" should {
    "create indexes for the repository" in {
      val repo = repositories.fsacScoresRepository

      val indexes = indexesWithFields(repo)
      indexes must contain (List("_id"))
      indexes must contain (List("applicationId"))
      indexes.size mustBe 2
    }

    val FsacScores = FSACAllExercisesScoresAndFeedbackExamples.Example1
    val AppId = FSACAllExercisesScoresAndFeedbackExamples.Example1.applicationId

    "create a new application scores and feedback document" in {
      repository.save(FsacScores).futureValue

      repository.find(AppId).futureValue mustBe Some(FsacScores)
    }

    "return already stored application scores" in {
      repository.save(FsacScores).futureValue

      val result = repository.find(AppId).futureValue

      result mustBe Some(FsacScores)
    }

    "return no application score if it does not exist" in {
      val result = repository.find(AppId).futureValue

      result mustBe None
    }

    "update already saved application scores and feedback document" in {
      repository.save(FsacScores).futureValue

      val updatedApplicationScores = FsacScores.copy(analysisExercise = None)
      repository.save(updatedApplicationScores).futureValue

      repository.find(AppId).futureValue mustBe Some(updatedApplicationScores)
    }

    "retrieve all application scores and feedback documents" in {
      val FsacScores2 = FSACAllExercisesScoresAndFeedbackExamples.Example2


      repository.save(FsacScores).futureValue
      repository.save(FsacScores2).futureValue

      val result = repository.findAll.futureValue

      result must have size 2
      result must contain (FsacScores)
      result must contain (FsacScores2)
    }
  }
}
