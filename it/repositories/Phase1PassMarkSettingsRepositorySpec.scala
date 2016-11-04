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

import model.exchange.passmarksettings.{ PassMarkThreshold, Phase1PassMark, Phase1PassMarkSettings, Phase1PassMarkThresholds }
import testkit.MongoRepositorySpec
import model.SchemeType._
import org.joda.time.DateTime
import repositories.passmarksettings.Phase1PassMarkSettingsMongoRepository

class Phase1PassMarkSettingsRepositorySpec extends MongoRepositorySpec {

  val collectionName = "phase1-pass-mark-settings"
  val version = "version-1"
  val createdDate = DateTime.now()
  val createdByUser = "user-1"
  val phase1PassMarkThresholds = Phase1PassMarkThresholds(PassMarkThreshold(20d, 80d), PassMarkThreshold(20d, 80d))
  val phase1PassMarks = List(Phase1PassMark(Finance, phase1PassMarkThresholds))
  val passMarkSettings = Phase1PassMarkSettings(phase1PassMarks, version, createdDate, createdByUser)

  def passMarkSettingsRepo = new Phase1PassMarkSettingsMongoRepository()

  "Pass-mark-settings collection" should {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repositories.phase1PassMarkSettingsRepository)
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
      val result = passMarkSettingsRepo.getLatestVersion.futureValue.get
      result mustBe passMarkSettings
    }

    "find the latest pass mark settings" in {
      val newPhase1PassMarkThresholds = Phase1PassMarkThresholds(PassMarkThreshold(30d, 80d), PassMarkThreshold(20d, 60d))
      val newPhase1PassMarks = List(Phase1PassMark(Finance, newPhase1PassMarkThresholds))
      val newVersion = "version-2"
      val newPassMarkSettings = passMarkSettings.copy(schemes = newPhase1PassMarks, newVersion, DateTime.now().plusDays(1), createdByUser)

      passMarkSettingsRepo.create(passMarkSettings).futureValue
      passMarkSettingsRepo.create(newPassMarkSettings).futureValue

      val result = passMarkSettingsRepo.getLatestVersion.futureValue.get
      result mustBe newPassMarkSettings
    }

    "no pass mark settings returned" in {
      val result = passMarkSettingsRepo.getLatestVersion.futureValue
      result mustBe None
    }
  }
}
