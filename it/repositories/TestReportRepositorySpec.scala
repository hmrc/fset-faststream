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

import model.OnlineTestCommands.TestResult
import model.PersistedObjects.CandidateTestReport
import testkit.MongoRepositorySpec


class TestReportRepositorySpec extends MongoRepositorySpec {
  import TestReportRepositorySpec._

  override val collectionName = "online-test-report"

  def repo = new TestReportMongoRepository()

  "TestReportRepository" should {

    "load and save CandidateTestReports" in {

      repo.saveOnlineTestReport(candidateTestReport).futureValue
      val actual = repo.getReportByApplicationId("appId").futureValue

      actual.get.applicationId mustBe "appId"
      actual.get.competency.get.status mustBe "Completed"
      actual.get.competency.get.norm mustBe "Test Norm"
      actual.get.competency.get.tScore.get mustBe 1.1d
      actual.get.competency.get.percentile.get mustBe 2.2d
      actual.get.competency.get.raw.get mustBe 3d
      actual.get.competency.get.sten.get mustBe 4d
    }

    "remove a report" in {
      repo.saveOnlineTestReport(candidateTestReport).futureValue
      repo.remove(candidateTestReport.applicationId).futureValue
      repo.getReportByApplicationId("appId").futureValue mustBe empty
    }
  }
}

object TestReportRepositorySpec {
  val candidateTestReport = CandidateTestReport("appId", "XML",
    competency = Some(TestResult("Completed", "Test Norm",
      tScore = Some(1.1),
      percentile = Some(2.2),
      raw = Some(3),
      sten = Some(4))))
}
