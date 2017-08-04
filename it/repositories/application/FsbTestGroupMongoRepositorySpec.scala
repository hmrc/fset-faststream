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

package repositories.application

import factories.UUIDFactory
import model.SchemeId
import model.persisted.{ FsbTestGroup, SchemeEvaluationResult }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.CollectionNames
import testkit.MongoRepositorySpec
import services.GBTimeZoneService
import config.MicroserviceAppConfig.cubiksGatewayConfig

class FsbTestGroupMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {
  import ImplicitBSONHandlers._

  val collectionName = CollectionNames.APPLICATION
  lazy val repository = new FsbTestGroupMongoRepository()

  "save" should {
    "create new FSB entry in testGroup if it doesn't exist" in {
      val applicationId = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult(SchemeId("schemeId"), "Green")
      repository.save(applicationId, schemeEvaluationResult).futureValue
      val Some(result) = repository.findByApplicationId(applicationId).futureValue
      val fsbTestGroup = FsbTestGroup(List(schemeEvaluationResult))
      result mustBe fsbTestGroup
    }

    "add to result array if result array already exist" in {
      val applicationId = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult(SchemeId("schemeId"), "Red")
      repository.save(applicationId, schemeEvaluationResult).futureValue

      val schemeEvaluationResult2 = SchemeEvaluationResult(SchemeId("schemeId2"), "Green")
      repository.save(applicationId, schemeEvaluationResult2).futureValue

      val Some(result) = repository.findByApplicationId(applicationId).futureValue
      val fsbTestGroup = FsbTestGroup(List(
        SchemeEvaluationResult(SchemeId("schemeId"), "Red"),
        SchemeEvaluationResult(SchemeId("schemeId2"), "Green")))
      result mustBe fsbTestGroup
    }

    "not overwrite existing value" in {
      val applicationId = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult(SchemeId("schemeId"), "Green")
      repository.save(applicationId, schemeEvaluationResult).futureValue

      intercept[Exception] {
        repository.save(applicationId, schemeEvaluationResult).futureValue
      }
    }
  }

  "findByApplicationId" should {
    "return the FsbTestGroup for the given applicationId" in {
      val applicationId = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult(SchemeId("schemeId"), "Green")
      repository.save(applicationId, schemeEvaluationResult).futureValue
      val Some(result) = repository.findByApplicationId(applicationId).futureValue
      val fsbTestGroup = FsbTestGroup(List(schemeEvaluationResult))
      result mustBe fsbTestGroup
    }

    "return None if FsbTestGroup is not found for the given applicationId" in {
      val applicationId = createApplication()
      val result = repository.findByApplicationId("appId-that-does-not-exist").futureValue
      result mustBe None
    }

    "return None if the given applicationId does not exist" in {
      val applicationId = createApplication()
      val result = repository.findByApplicationId("appId-that-does-not-exist").futureValue
      result mustBe None
    }
  }

  private def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService, cubiksGatewayConfig)

  private def createApplication(): String = {
    val applicationId = generateUUID()
    helperRepo.collection.insert(BSONDocument("applicationId" -> applicationId)).futureValue
    applicationId
  }

}
