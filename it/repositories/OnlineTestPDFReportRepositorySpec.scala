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

import testkit.MongoRepositorySpec

class OnlineTestPDFReportRepositorySpec extends MongoRepositorySpec {

  override val collectionName = "online-test-pdf-report"

  val ApplicationId1 = "1111-1111"
  val ApplicationId2 = "2222-2222"

  val OnlineTestPDFReportNotFound = "NotFound"
  val Content = Array[Byte](0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20)

  def onlineTestPDFReportRepo = new OnlineTestPDFReportMongoRepository()

  "Online Test PDF Report collection" should {
    "create indexes for the repository" in {
      val repo = repositories.onlineTestPDFReportRepository

      val indexes = indexesWithFields(repo)
      indexes must contain (List("_id"))
      indexes must contain (List("applicationId"))
      indexes.size must be (2)
    }

  }

  "get" should {
    "return no online test pdf report if online-test-pdf-report collection is empty" in {
      val result = onlineTestPDFReportRepo.get(ApplicationId1).futureValue
      result must be(None)
    }
  }

  "save and get" should {
    "return no online test pdf report if report is not found" in {
      onlineTestPDFReportRepo.save(ApplicationId1, Content)
      val result = onlineTestPDFReportRepo.get(OnlineTestPDFReportNotFound).futureValue
      result must be(None)
    }

    "return online test pdf report when report exist and there is only one report" in {
      onlineTestPDFReportRepo.save(ApplicationId1, Content).futureValue
      val result = onlineTestPDFReportRepo.get(ApplicationId1).futureValue
      result.get.deep must be (Content.deep)
    }

    "return corresponding online test pdf report when report exist and there are several reports" in {
      onlineTestPDFReportRepo.save(ApplicationId1, Content).futureValue
      onlineTestPDFReportRepo.save(ApplicationId2, Content).futureValue

      val result = onlineTestPDFReportRepo.get(ApplicationId2).futureValue
      result.get.deep must be (Content.deep)
    }
  }

  "has report" should {
    "return true if there is a report" in {
      onlineTestPDFReportRepo.save(ApplicationId2, Content).futureValue
      onlineTestPDFReportRepo.hasReport(ApplicationId2).futureValue must equal(true)
    }

    "return false if there is no report" in {
      onlineTestPDFReportRepo.hasReport(ApplicationId1).futureValue must equal(false)
    }
  }

  "remove" should {
    "remove a PDF report" in {
      onlineTestPDFReportRepo.save(ApplicationId1, Content).futureValue
      onlineTestPDFReportRepo.remove(ApplicationId1).futureValue
      onlineTestPDFReportRepo.hasReport(ApplicationId1).futureValue must equal(false)
    }
  }
}
