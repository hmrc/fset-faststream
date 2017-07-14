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
import model.FSACScores.{CandidateScores, CandidateScoresAndFeedback}
import testkit.MongoRepositorySpec

class FSACScoresRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.FSAC_SCORES

  def repository = new FSACScoresMongoRepository(DateTimeFactory)

  "Application Scores Repository" should {
    "create indexes for the repository" in {
      val repo = repositories.fsacScoresRepository

      val indexes = indexesWithFields(repo)
      indexes must contain (List("_id"))
      indexes must contain (List("applicationId"))
      indexes.size mustBe 2
    }

    val CandidateScoresWithFeedback = CandidateScoresAndFeedback("app1", Some(true), assessmentIncomplete = false,
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)),
      CandidateScores(Some(4.0), Some(3.0), Some(2.0)))

    "create a new application scores and feedback document" in {
      repository.update(CandidateScoresWithFeedback).futureValue

      repository.find("app1").futureValue mustBe Some(CandidateScoresWithFeedback)
    }

    "return already stored application scores" in {
      repository.update(CandidateScoresWithFeedback).futureValue

      val result = repository.find("app1").futureValue

      result mustBe Some(CandidateScoresWithFeedback)
    }

    "return no application score if it does not exist" in {
      val result = repository.find("app1").futureValue

      result mustBe None
    }

    "update already saved application scores and feedback document" in {
      repository.update(CandidateScoresWithFeedback).futureValue
      val updatedApplicationScores = CandidateScoresWithFeedback.copy(attendancy = Some(false))

      repository.update(updatedApplicationScores).futureValue

      repository.find("app1").futureValue mustBe Some(updatedApplicationScores)
    }

    "retrieve all application scores and feedback documents" in {
      val CandidateScoresWithFeedback2 = CandidateScoresAndFeedback("app2", Some(true), assessmentIncomplete = false,
        CandidateScores(Some(1.0), Some(3.0), Some(2.0)),
        CandidateScores(Some(1.0), Some(3.0), Some(2.0)),
        CandidateScores(Some(1.0), Some(3.0), Some(2.0)),
        CandidateScores(Some(1.0), Some(3.0), Some(2.0)),
        CandidateScores(Some(1.0), Some(3.0), Some(2.0)),
        CandidateScores(Some(1.0), Some(3.0), Some(2.0)),
        CandidateScores(Some(1.0), Some(3.0), Some(2.0)))

      repository.update(CandidateScoresWithFeedback).futureValue
      repository.update(CandidateScoresWithFeedback2).futureValue

      val result = repository.findAll.futureValue

      result must have size 2
      result must contain ("app1" -> CandidateScoresWithFeedback)
      result must contain ("app2" -> CandidateScoresWithFeedback2)
    }
  }
}
