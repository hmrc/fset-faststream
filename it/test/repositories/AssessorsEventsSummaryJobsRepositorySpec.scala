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

package repositories

import model.AssessorNewEventsJobInfo
import testkit.MongoRepositorySpec

class AssessorsEventsSummaryJobsRepositorySpec extends MongoRepositorySpec with CommonRepository {
  override val collectionName: String = CollectionNames.ASSESSOR_EVENTS_SUMMARY_JOBS

  lazy val repository = new AssessorsEventsSummaryJobsMongoRepository(mongo)

  "AssessorsEventsSummaryJobs" should {
    "save only the last run job" in {
      val futureTime = now.plusMonths(1)
      val dateTimes = Seq(now, now.plusDays(1), now.plusDays(2), now.plusDays(3), futureTime)

      dateTimes.foreach { dateTime =>
        repository.save(AssessorNewEventsJobInfo(dateTime)).futureValue mustBe unit
      }

      repository.lastRun.futureValue mustBe Option(AssessorNewEventsJobInfo(futureTime))
    }
  }
}
