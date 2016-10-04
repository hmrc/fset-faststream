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

import model.exchange.passmarksettings.{ PassMarkThreshold, SchemePassMark, SchemePassMarkSettings, SchemePassMarkThresholds }
import testkit.MongoRepositorySpec
import model.SchemeType._
import org.joda.time.DateTime

class PassMarkSettingsRepositorySpec extends MongoRepositorySpec {

  val collectionName = "pass-mark-settings"
  val version = "version-1"
  val createdDate = DateTime.now()
  val createdByUser = "user-1"
  val schemePassMarkThresholds = SchemePassMarkThresholds(PassMarkThreshold(20d, 80d), PassMarkThreshold(20d, 80d))
  val schemePassMarks = List(SchemePassMark(Finance, schemePassMarkThresholds))
  val passMarkSettings = SchemePassMarkSettings(schemePassMarks, version, createdDate, createdByUser, "any")

  def passMarkSettingsRepo = new PassMarkSettingsMongoRepository()

  "Pass-mark-settings collection" should {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repositories.passMarkSettingsRepository)
      indexes must contain (List("_id"))
      indexes must contain (List("createDate"))
      indexes.size must be (2)
    }
  }

  "Pass mark settings repo" should {
    "create the pass mark settings" in {
      val passMarkSettingCreationResponse = passMarkSettingsRepo.create(passMarkSettings).futureValue
      passMarkSettingCreationResponse.passMarkSettingsCreateDate mustBe createdDate
      passMarkSettingCreationResponse.passMarkSettingsVersion mustBe version
    }

    "find the pass mark settings" in {
      passMarkSettingsRepo.create(passMarkSettings).futureValue
      val result = passMarkSettingsRepo.tryGetLatestVersion.futureValue.get
      result mustBe passMarkSettings
    }

    "find the latest pass mark settings" in {
      val newSchemePassMarkThresholds = SchemePassMarkThresholds(PassMarkThreshold(30d, 80d), PassMarkThreshold(20d, 60d))
      val newSchemePassMarks = List(SchemePassMark(Finance, newSchemePassMarkThresholds))
      val newVersion = "version-2"
      val newPassMarkSettings = passMarkSettings.copy(schemes = newSchemePassMarks, newVersion, DateTime.now(), createdByUser, "any")

      passMarkSettingsRepo.create(passMarkSettings).futureValue
      passMarkSettingsRepo.create(newPassMarkSettings).futureValue

      val result = passMarkSettingsRepo.tryGetLatestVersion.futureValue.get
      result mustBe newPassMarkSettings
    }

    "no pass mark settings returned" in {
      val result = passMarkSettingsRepo.tryGetLatestVersion.futureValue
      result mustBe None
    }
  }
}
