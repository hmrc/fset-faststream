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
import factories.{ DateTimeFactory, UUIDFactory }
import model.EvaluationResults.{ Green, Red }
import model.SchemeId
import model.persisted._
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class FsbTestGroupMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  import ImplicitBSONHandlers._

  val collectionName = CollectionNames.APPLICATION
  def repository = new FsbTestGroupMongoRepository()

  "save" should {
    "create new FSB entry in testGroup if it doesn't exist" in {
      val applicationId = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult("GovernmentOperationalResearchService", "Green")
      repository.save(applicationId, schemeEvaluationResult).futureValue
      val Some(result) = repository.findByApplicationId(applicationId).futureValue
      val fsbTestGroup = FsbTestGroup(List(schemeEvaluationResult))
      result mustBe fsbTestGroup
    }

    "add to result array if result array already exist" in {
      val applicationId = createApplication()
      val schemeEvaluationResult1 = SchemeEvaluationResult("GovernmentOperationalResearchService", "Red")
      repository.save(applicationId, schemeEvaluationResult1).futureValue

      val schemeEvaluationResult2 = SchemeEvaluationResult("GovernmentSocialResearchService", "Green")
      repository.save(applicationId, schemeEvaluationResult2).futureValue

      val Some(result) = repository.findByApplicationId(applicationId).futureValue
      val expectedFsbTestGroup = FsbTestGroup(List(schemeEvaluationResult1, schemeEvaluationResult2))
      result mustBe expectedFsbTestGroup
    }

    "not overwrite existing value" in {
      val applicationId = createApplication()
      repository.save(applicationId, SchemeEvaluationResult("GovernmentOperationalResearchService", "Green")).futureValue

      intercept[Exception] {
        repository.save(applicationId,  SchemeEvaluationResult("GovernmentOperationalResearchService", "Red")).futureValue
      }
    }

  }

  "findByApplicationId" should {
    "return the FsbTestGroup for the given applicationId" in {
      val applicationId = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult("GovernmentOperationalResearchService", "Green")
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
    "return the FsbSchemeResult for the given applicationIds" in {
      val applicationId1 = createApplication()
      val applicationId2 = createApplication()
      val applicationId3 = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult("GovernmentOperationalResearchService", Green.toString)

      repository.save(applicationId1, schemeEvaluationResult).futureValue
      repository.save(applicationId2, schemeEvaluationResult.copy(result = Red.toString)).futureValue
      repository.save(applicationId3, schemeEvaluationResult).futureValue

      val result = repository.findByApplicationIds(List(applicationId1, applicationId2, applicationId3), None).futureValue

      val expectedResult = List(
        FsbSchemeResult(applicationId1, List(schemeEvaluationResult)),
        FsbSchemeResult(applicationId2, List(schemeEvaluationResult.copy(result = Red.toString))),
        FsbSchemeResult(applicationId3, List(schemeEvaluationResult))
      )

      result must contain theSameElementsAs expectedResult
    }

    "return the FsbSchemeResult for the given applicationIds filtered by scheme" in {
      val applicationId1 = createApplication()
      val applicationId2 = createApplication()

      repository.save(applicationId1, SchemeEvaluationResult("GovernmentOperationalResearchService", Red.toString)).futureValue
      repository.save(applicationId1, SchemeEvaluationResult("GovernmentSocialResearchService", Green.toString)).futureValue
      repository.save(applicationId2, SchemeEvaluationResult("GovernmentOperationalResearchService", Green.toString)).futureValue

      val result = repository.findByApplicationIds(
        List(applicationId1, applicationId2), Some(SchemeId("GovernmentOperationalResearchService"))).futureValue

      val expectedResult = List(
        FsbSchemeResult(applicationId1, List(SchemeEvaluationResult("GovernmentOperationalResearchService", "Red"))),
        FsbSchemeResult(applicationId2, List(SchemeEvaluationResult("GovernmentOperationalResearchService", "Green")))
      )

      result must contain theSameElementsAs expectedResult
    }

  }

  private def applicationRepository = new GeneralApplicationMongoRepository(DateTimeFactory, cubiksGatewayConfig)

  private def createApplication(): String = {
    val applicationId = generateUUID()
    applicationRepository.collection.insert(BSONDocument("applicationId" -> applicationId, "userId" -> generateUUID()))
    applicationId
  }

}
