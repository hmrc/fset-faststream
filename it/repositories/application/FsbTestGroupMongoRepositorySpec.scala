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

import config.MicroserviceAppConfig.cubiksGatewayConfig
import factories.UUIDFactory
import model.EvaluationResults.{ Green, Red }
import model.SchemeId
import model.persisted.{ FsbEvaluation, FsbResult, FsbTestGroup, SchemeEvaluationResult }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.CollectionNames
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

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
        repository.save(applicationId, schemeEvaluationResult.copy(result = "Red")).futureValue
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
      val result = repository.findByApplicationId("appId-with-no-fsb-test-group").futureValue
      result mustBe None
    }

    "return None if the given applicationId does not exist" in {
      repository.findByApplicationId("appId-that-does-not-exist").futureValue mustBe None
    }
  }

  "findByApplicationIds" should {
    "return the FsbTestGroup for the given applicationIds" in {
      val applicationId1 = createApplication()
      val applicationId2 = createApplication()
      val applicationId3 = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult(SchemeId("schemeId-test"), Green.toString)

      repository.save(applicationId1, schemeEvaluationResult).futureValue
      repository.save(applicationId2, schemeEvaluationResult.copy(result = Red.toString)).futureValue
      repository.save(applicationId3, schemeEvaluationResult).futureValue

      val result = repository.findByApplicationIds(List(applicationId1, applicationId2, applicationId3)).futureValue

      val expectedResult = List(
        FsbResult(applicationId1, FsbEvaluation(List(schemeEvaluationResult))),
        FsbResult(applicationId2, FsbEvaluation(List(schemeEvaluationResult.copy(result = Red.toString)))),
        FsbResult(applicationId3, FsbEvaluation(List(schemeEvaluationResult)))
      )

      result must contain theSameElementsAs expectedResult
    }
  }

  private def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService, cubiksGatewayConfig)

  private def createApplication(): String = {
    val applicationId = generateUUID()
    helperRepo.collection.insert(BSONDocument("applicationId" -> applicationId, "userId" -> generateUUID()))
    applicationId
  }

}
