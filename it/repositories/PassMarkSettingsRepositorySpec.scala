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

import model.exchange.passmarksettings._
import testkit.{ ExtendedTimeout, MongoRepositorySpec }
import model.SchemeType._
import org.joda.time.DateTime
import play.api.libs.json.Format
import repositories.passmarksettings.{ PassMarkSettingsRepository, Phase1PassMarkSettingsMongoRepository, Phase2PassMarkSettingsMongoRepository }
import uk.gov.hmrc.mongo.ReactiveRepository

class Phase1PassMarkSettingsRepositorySpec extends PassMarkRepositoryFixture {
  type T = Phase1PassMarkSettings
  type U = Phase1PassMark
  implicit val formatter = Phase1PassMarkSettings.phase1PassMarkSettingsFormat
  val phase1PassMarkThresholds = Phase1PassMarkThresholds(PassMarkThreshold(20d, 80d), PassMarkThreshold(20d, 80d))
  val phase1PassMarks = List(Phase1PassMark(Finance, phase1PassMarkThresholds))
  val passMarkSettings = Phase1PassMarkSettings(phase1PassMarks, version, createdDate, createdByUser)
  val newPassMarkThresholds = Phase1PassMarkThresholds(PassMarkThreshold(30d, 80d), PassMarkThreshold(20d, 60d))
  val newPassMarks = List(Phase1PassMark(Finance, newPassMarkThresholds))
  def passMarkSettingsRepo = new Phase1PassMarkSettingsMongoRepository()
  val collectionName = "phase1-pass-mark-settings"

  override def copyNewPassMarkSettings(o: Phase1PassMarkSettings, newPassMarks: List[Phase1PassMark], newVersion: String, newDate:
  DateTime, newUser: String): Phase1PassMarkSettings = {
    o.copy(schemes = newPassMarks, newVersion, DateTime.now().plusDays(1), createdByUser)
  }
}

class Phase2PassMarkSettingsRepositorySpec extends PassMarkRepositoryFixture {
  type T = Phase2PassMarkSettings
  type U = Phase2PassMark
  implicit val formatter = Phase2PassMarkSettings.phase2PassMarkSettingsFormat
  val phase2PassMarkThresholds = Phase2PassMarkThresholds(PassMarkThreshold(20d, 80d))
  val phase2PassMarks = List(Phase2PassMark(Finance, phase2PassMarkThresholds))
  val passMarkSettings = Phase2PassMarkSettings(phase2PassMarks, version, createdDate, createdByUser)
  val newPassMarkThresholds = Phase2PassMarkThresholds(PassMarkThreshold(30d, 80d))
  val newPassMarks = List(Phase2PassMark(Finance, newPassMarkThresholds))
  def passMarkSettingsRepo = new Phase2PassMarkSettingsMongoRepository()
  val collectionName = "phase2-pass-mark-settings"

  override def copyNewPassMarkSettings(o: Phase2PassMarkSettings, newPassMarks: List[Phase2PassMark], newVersion: String, newDate:
    DateTime, newUser: String): Phase2PassMarkSettings = {
    o.copy(schemes = newPassMarks, newVersion, DateTime.now().plusDays(1), createdByUser)
  }
}

trait PassMarkRepositoryFixture extends MongoRepositorySpec {
  type T <: PassMarkSettings
  type U <: PassMark
  implicit val formatter: Format[T]
  val passMarkSettings: T
  val newPassMarkThresholds: PassMarkThresholds
  val newPassMarks: List[U]
  def passMarkSettingsRepo: PassMarkSettingsRepository[T]

  def copyNewPassMarkSettings(o: T, schemes: List[U], newVersion: String, newDate: DateTime, newUser: String): T

  val collectionName: String
  val version = "version-1"
  val createdDate = DateTime.now()
  val createdByUser = "user-1"


  "Pass-mark-settings collection" should {
    "create indicies for the repository" in {
      val indexes = indexesWithFields(passMarkSettingsRepo.asInstanceOf[ReactiveRepository[_, _]])
      indexes must contain (List("_id"))
      indexes must contain (List("createDate"))
      indexes.size mustBe 2
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
      val newVersion = "version-2"
      val newPassMarkSettings = copyNewPassMarkSettings(passMarkSettings, newPassMarks, newVersion,
       DateTime.now.plusDays(1), createdByUser)

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
