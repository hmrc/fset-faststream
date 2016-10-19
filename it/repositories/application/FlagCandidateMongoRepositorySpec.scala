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

package repositories.application

import factories.UUIDFactory
import model.Exceptions.NotFoundException
import model.FlagCandidatePersistedObject.FlagCandidate
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import services.GBTimeZoneService
import config.MicroserviceAppConfig._
import testkit.MongoRepositorySpec

class FlagCandidateMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {
  import ImplicitBSONHandlers._

  val collectionName = "application"
  def repository = new FlagCandidateMongoRepository
  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService, cubiksGatewayConfig, GeneralApplicationRepoBSONToModelHelper)

  "Flag Candidate repository" should {
    "create and get an issue for the candidate" in {
      val appId = createApplication()
      val issue = "An issue for this candidate"
      val flagCandidate = FlagCandidate(appId, Some(issue))

      repository.save(flagCandidate).futureValue

      val actualIssue = repository.tryGetCandidateIssue(appId).futureValue
      actualIssue must not be empty
      actualIssue must be (Some(flagCandidate))
    }

    "update and get an issue for the candidate" in {
      val appId = createApplication()
      val issue1 = "An issue for this candidate version 1"
      repository.save(FlagCandidate(appId, Some(issue1))).futureValue
      val issue2 = "An issue for this candidate version 2"
      repository.save(FlagCandidate(appId, Some(issue2))).futureValue

      val actualIssue = repository.tryGetCandidateIssue(appId).futureValue
      actualIssue must not be empty
      actualIssue must be (Some(FlagCandidate(appId, Some(issue2))))
    }

    "return an exception when create an issue for application which does not exist" in {
      val appId = "incorrect-AppId"
      val issue = "An issue for this candidate version"

      val result = repository.save(FlagCandidate(appId, Some(issue)))

      result.failed.futureValue mustBe a[NotFoundException]
    }

    "return None if there is no issue" in {
      val appId = createApplication()
      val actualIssue = repository.tryGetCandidateIssue(appId).futureValue

      actualIssue must be (empty)
    }

    "return None if there is no application" in {
      val appId = "no-app-id"
      val actualIssue = repository.tryGetCandidateIssue(appId).futureValue

      actualIssue must be (empty)
    }

    "remove an issue" in {
      val appId = createApplication()
      val issue1 = "An issue for this candidate version"
      repository.save(FlagCandidate(appId, Some(issue1))).futureValue

      repository.remove(appId).futureValue

      val actualIssue = repository.tryGetCandidateIssue(appId).futureValue
      actualIssue must be (empty)
    }

    "return an exception when remove an issue for application which does not exist" in {
      val appId = "incorrect-AppId"
      val result = repository.remove(appId)
      result.failed.futureValue mustBe a[NotFoundException]
    }
  }

  def createApplication() = {
    val appId = generateUUID()
    helperRepo.collection.insert(BSONDocument("applicationId" -> appId)).futureValue
    appId
  }

}
