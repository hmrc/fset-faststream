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

package repositories.onboardquestions

import factories.{ITDateTimeFactoryMock, UUIDFactory}
import model.Exceptions.CannotUpdateRecord
import model.persisted.OnboardQuestions
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import repositories.application.GeneralApplicationMongoRepository
import testkit.MongoRepositorySpec

class OnboardQuestionsMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  val collectionName: String = CollectionNames.APPLICATION
  def repository = new OnboardQuestionsMongoRepository(mongo)
  def helperRepo = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  "Onboard questions repository" should {
    "save and fetch onboarding questions for the candidate" in {
      val appId = createApplication()
      val niNumber = "NR998877B"
      val onboardQuestions = OnboardQuestions(appId, Some(niNumber))

      repository.save(onboardQuestions).futureValue

      val actualIssue = repository.tryGetOnboardQuestions(appId).futureValue
      actualIssue mustBe Some(onboardQuestions)
    }

    "return None if there are no onboard questions" in {
      val appId = createApplication()
      val onboardQuestions = repository.tryGetOnboardQuestions(appId).futureValue

      onboardQuestions mustBe empty
    }

    "return an exception when saving onboard questions for an application which does not exist" in {
      val appId = "incorrect-AppId"
      val niNumber = "NR998877B"
      val onboardQuestions = OnboardQuestions(appId, Some(niNumber))

      val result = repository.save(onboardQuestions)
      result.failed.futureValue mustBe a[CannotUpdateRecord]
    }
  }

  def createApplication(): String = {
    val appId = generateUUID()
    val doc = Document("applicationId" -> appId)
    helperRepo.applicationCollection.insertOne(doc).toFuture().futureValue
    appId
  }
}
