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

import model.PassmarkPersistedObjects._
import org.joda.time.{DateTime, DateTimeZone}
import testkit.MongoRepositorySpec

class AssessmentCentrePassMarkSettingsRepositorySpec extends MongoRepositorySpec {

  override val collectionName = "assessment-centre-pass-mark-settings"

  def repository = new AssessmentCentrePassMarkSettingsMongoRepository()

  "Assessment Centre Pass Mark settings repository" should {
    "create indexes for the repository" in {
      val repo = repositories.assessmentCentrePassMarkSettingsRepository

      val indexes = indexesWithFields(repo)
      indexes must contain (List("_id"))
      indexes must contain (List("info.createDate"))
      indexes.size mustBe 2
    }

    "return None if the pass mark settings do not exist" in {
      val result = repository.tryGetLatestVersion.futureValue

      result mustBe None
    }

    "create and fetch the passmark settings" in {
      val info = AssessmentCentrePassMarkInfo("123", DateTime.now(DateTimeZone.UTC), "userName")
      val settings = AssessmentCentrePassMarkSettings(List(
        AssessmentCentrePassMarkScheme("Business", Some(PassMarkSchemeThreshold(20.05, 40.06)))
      ), info)

      repository.create(settings).futureValue
      val result = repository.tryGetLatestVersion.futureValue

      result.get mustBe settings
    }

    "create two different version of pass mark settings and return the newest" in {
      val olderInfo = AssessmentCentrePassMarkInfo("123", DateTime.now(DateTimeZone.UTC).minusDays(3), "userName")
      val olderSettings = AssessmentCentrePassMarkSettings(List(
        AssessmentCentrePassMarkScheme("Commercial", Some(PassMarkSchemeThreshold(20.05, 40.06)))
      ), olderInfo)

      val newerInfo = AssessmentCentrePassMarkInfo("456", DateTime.now(DateTimeZone.UTC), "userName")
      val newerSettings = AssessmentCentrePassMarkSettings(List(
        AssessmentCentrePassMarkScheme("Commercial", Some(PassMarkSchemeThreshold(30.05, 35.06)))
      ), newerInfo)

      repository.create(newerSettings).futureValue
      repository.create(olderSettings).futureValue
      val result = repository.tryGetLatestVersion.futureValue

      result.get mustBe newerSettings
    }
  }
}
