/*
 * Copyright 2024 HM Revenue & Customs
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

package repositories.sift

import model.Exceptions.{NotFoundException, SiftAnswersIncomplete}
import model.SchemeId
import model.persisted.sift._
import org.scalatest.concurrent.ScalaFutures
import repositories.{CollectionNames, CommonRepository}
import testkit.{MockitoSugar, MongoRepositorySpec}

class SiftAnswersRepositorySpec extends MongoRepositorySpec with ScalaFutures with CommonRepository with MockitoSugar {

  val collectionName: String = CollectionNames.SIFT_ANSWERS

  def repository: SiftAnswersMongoRepository = siftAnswersRepository

  "sift answers repository" should {

    "create indexes" in {
      val indexes = indexDetails(repository).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "applicationId_1", keys = Seq(("applicationId", "Ascending")), unique = true)
        )
    }

    "handle no answers saved" in {
      repository.findSiftAnswersStatus(AppId).futureValue mustBe None
      repository.findSchemeSpecificAnswer(AppId, Commercial).futureValue mustBe None
      repository.findGeneralQuestionsAnswers(AppId).futureValue mustBe None
      repository.findSiftAnswersStatus(AppId).futureValue mustBe None
    }

    "save and fetch general answers no optional data specified" in {
      val generalAnswers = GeneralQuestionsAnswers(
        multipleNationalities = true,
        secondNationality = None,
        nationality = "British",
        undergradDegree = None,
        postgradDegree = None
      )
      commonGeneralAnswersTest(generalAnswers)
    }

    "save and fetch general answers no degrees specified" in {
      val generalAnswers = GeneralQuestionsAnswers(
        multipleNationalities = true,
        secondNationality = Some("French"),
        nationality = "British",
        undergradDegree = None,
        postgradDegree = None
      )
      commonGeneralAnswersTest(generalAnswers)
    }

    "save and fetch general answers all data specified" in {
      val generalAnswers = GeneralQuestionsAnswers(
        multipleNationalities = true,
        secondNationality = Some("French"),
        nationality = "British",
        undergradDegree = Some(UndergradDegreeInfoAnswers("BSC Computer Science", "2:1", "1992", Some("Module details"))),
        postgradDegree = Some(PostGradDegreeInfoAnswers("Phd Computer Science", "1995", Some("Other details"), Some("Project details")))
      )
      commonGeneralAnswersTest(generalAnswers)
    }

    def commonGeneralAnswersTest(generalAnswers: GeneralQuestionsAnswers) = {
      repository.addGeneralAnswers(AppId, generalAnswers).futureValue

      repository.findGeneralQuestionsAnswers(AppId).futureValue mustBe Some(generalAnswers)

      repository.findSiftAnswersStatus(AppId).futureValue mustBe Some(SiftAnswersStatus.DRAFT)

      val savedSiftAnswers = repository.findSiftAnswers(AppId).futureValue

      savedSiftAnswers mustBe Some(SiftAnswers(
        AppId, SiftAnswersStatus.DRAFT, Some(generalAnswers), schemeAnswers = Map.empty[String, SchemeSpecificAnswer]
      ))
    }
  }

  "attempt to update the candidate's sift status to submitted" should {
    "throw an exception if no general answers are already saved when attempting to save sift answers with no required schemes" in {
      val result = repository.submitAnswers(AppId, requiredSchemes = Set.empty[SchemeId]).failed.futureValue
      result mustBe a[SiftAnswersIncomplete]
    }

    "throw an exception if no general answers are already saved when attempting to save sift answers with required schemes" in {
      val result = repository.submitAnswers(AppId, requiredSchemes = Set(Commercial)).failed.futureValue
      result mustBe a[SiftAnswersIncomplete]
    }

    "throw an exception when there are scheme specific answers for the required schemes but no general answers" in {
      repository.addSchemeSpecificAnswer("AppId", Commercial, SchemeSpecificAnswer("Answer text")).futureValue
      val result = repository.submitAnswers(AppId, requiredSchemes = Set(Commercial)).failed.futureValue
      result mustBe a[SiftAnswersIncomplete]
    }

    "throw an exception when there are no scheme specific answers but general answers have been saved" in {
      val answers = GeneralQuestionsAnswers(
        multipleNationalities = false,
        secondNationality = None,
        nationality = "British",
        undergradDegree = None,
        postgradDegree = None
      )

      repository.addGeneralAnswers("AppId", answers).futureValue
      val result = repository.submitAnswers(AppId, requiredSchemes = Set(Commercial)).failed.futureValue
      result mustBe a[SiftAnswersIncomplete]
    }

    "successfully update the candidate when both scheme specific answers and general answers have been saved" in {
      val answers = GeneralQuestionsAnswers(
        multipleNationalities = false,
        secondNationality = None,
        nationality = "British",
        undergradDegree = None,
        postgradDegree = None
      )

      repository.addGeneralAnswers("AppId", answers).futureValue
      repository.addSchemeSpecificAnswer("AppId", Commercial, SchemeSpecificAnswer("Answer text")).futureValue
      repository.submitAnswers(AppId, requiredSchemes = Set(Commercial)).futureValue mustBe unit
      repository.findSiftAnswersStatus(AppId).futureValue mustBe Some(SiftAnswersStatus.SUBMITTED)
    }
  }

  "remove sift answers" should {
    "remove the answers as expected" in {
      repository.addSchemeSpecificAnswer("AppId", Commercial, SchemeSpecificAnswer("Answer text")).futureValue
      repository.findSchemeSpecificAnswer(AppId, Commercial).futureValue mustBe Some(SchemeSpecificAnswer("Answer text"))
      repository.removeSiftAnswers(AppId).futureValue
      repository.findSchemeSpecificAnswer(AppId, Commercial).futureValue mustBe None
    }
  }

  "set sift answers status" should {
    "throw an exception if the application cannopt be found" in {
      val result = repository.setSiftAnswersStatus(AppId, SiftAnswersStatus.SUBMITTED).failed.futureValue
      result mustBe a[NotFoundException]
    }

    "update the status as expected" in {
      repository.addSchemeSpecificAnswer("AppId", Commercial, SchemeSpecificAnswer("Answer text")).futureValue
      repository.findSiftAnswersStatus(AppId).futureValue mustBe Some(SiftAnswersStatus.DRAFT)
      repository.setSiftAnswersStatus(AppId, SiftAnswersStatus.SUBMITTED).futureValue
      repository.findSiftAnswersStatus(AppId).futureValue mustBe Some(SiftAnswersStatus.SUBMITTED)
    }
  }
}
