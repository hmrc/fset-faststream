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

import model.{SchemeId, Schemes}
import model.exchange.passmarksettings._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, OFormat}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import repositories.passmarksettings._
import testkit.MongoRepositorySpec

class Phase1PassMarkSettingsRepositorySpec extends PassMarkRepositoryFixture with Schemes {
  override type T = Phase1PassMarkSettingsPersistence
  override type U = Phase1PassMark
  override implicit val formatter: OFormat[Phase1PassMarkSettingsPersistence] = Phase1PassMarkSettingsPersistence.jsonFormat
  val phase1PassMarkThresholds = Phase1PassMarkThresholds(
    PassMarkThreshold(20d, 80d), PassMarkThreshold(20d, 80d), PassMarkThreshold(20d, 80d)
  )
  val phase1PassMarks = List(Phase1PassMark(Finance, phase1PassMarkThresholds))
  override val passMarkSettings = Phase1PassMarkSettingsPersistence(phase1PassMarks, version, createdDate, createdByUser)
  override val newPassMarkThresholds = Phase1PassMarkThresholds(
    PassMarkThreshold(30d, 80d), PassMarkThreshold(20d, 60d), PassMarkThreshold(30d, 80d)
  )
  override val newPassMarks = List(Phase1PassMark(Finance, newPassMarkThresholds))
  override def passMarkSettingsRepo = new Phase1PassMarkSettingsMongoRepository(mongo)
  override val collectionName: String = CollectionNames.PHASE1_PASS_MARK_SETTINGS

  override def copyNewPassMarkSettings(passMarks: Phase1PassMarkSettingsPersistence, newPassMarks: List[Phase1PassMark],
                                                newVersion: String, newDate: DateTime, newUser: String): Phase1PassMarkSettingsPersistence = {
    passMarks.copy(schemes = newPassMarks, newVersion, createdDate.plusDays(1), createdByUser)
  }
}

class Phase2PassMarkSettingsRepositorySpec extends PassMarkRepositoryFixture with Schemes {
  override type T = Phase2PassMarkSettingsPersistence
  override type U = Phase2PassMark
  override implicit val formatter: OFormat[Phase2PassMarkSettingsPersistence] = Phase2PassMarkSettingsPersistence.jsonFormat
  val phase2PassMarkThresholds = Phase2PassMarkThresholds(PassMarkThreshold(20d, 80d), PassMarkThreshold(20d, 80d))
  val phase2PassMarks = List(Phase2PassMark(Finance, phase2PassMarkThresholds))
  override val passMarkSettings = Phase2PassMarkSettingsPersistence(phase2PassMarks, version, createdDate, createdByUser)
  override val newPassMarkThresholds = Phase2PassMarkThresholds(PassMarkThreshold(30d, 80d), PassMarkThreshold(30d, 80d))
  override val newPassMarks = List(Phase2PassMark(Finance, newPassMarkThresholds))
  override def passMarkSettingsRepo = new Phase2PassMarkSettingsMongoRepository(mongo)
  override val collectionName: String = CollectionNames.PHASE2_PASS_MARK_SETTINGS

  override def copyNewPassMarkSettings(passMarks: Phase2PassMarkSettingsPersistence, newPassMarks: List[Phase2PassMark],
                                       newVersion: String, newDate: DateTime, newUser: String): Phase2PassMarkSettingsPersistence = {
    passMarks.copy(schemes = newPassMarks, newVersion, createdDate.plusDays(1), createdByUser)
  }
}

class Phase3PassMarkSettingsRepositorySpec extends PassMarkRepositoryFixture with Schemes {
  override type T = Phase3PassMarkSettingsPersistence
  override type U = Phase3PassMark
  override implicit val formatter: OFormat[Phase3PassMarkSettingsPersistence] = Phase3PassMarkSettingsPersistence.jsonFormat
  val phase3PassMarkThresholds = Phase3PassMarkThresholds(PassMarkThreshold(20d, 80d))
  val phase3PassMarks = List(Phase3PassMark(Finance, phase3PassMarkThresholds))
  override val passMarkSettings = Phase3PassMarkSettingsPersistence(phase3PassMarks, version, createdDate, createdByUser)
  override val newPassMarkThresholds = Phase3PassMarkThresholds(PassMarkThreshold(30d, 80d))
  override val newPassMarks = List(Phase3PassMark(Finance, newPassMarkThresholds))
  override def passMarkSettingsRepo = new Phase3PassMarkSettingsMongoRepository(mongo)
  override val collectionName: String = CollectionNames.PHASE3_PASS_MARK_SETTINGS

  override def copyNewPassMarkSettings(passMarks: Phase3PassMarkSettingsPersistence, newPassMarks: List[Phase3PassMark],
                                       newVersion: String, newDate: DateTime, newUser: String): Phase3PassMarkSettingsPersistence = {
    passMarks.copy(schemes = newPassMarks, newVersion, createdDate.plusDays(1), createdByUser)
  }
}

class AssessmentCentrePassMarkSettingsRepositorySpec extends PassMarkRepositoryFixture with Schemes {
  type T = AssessmentCentrePassMarkSettingsPersistence
  type U = AssessmentCentreExercisePassMark
  override implicit val formatter: OFormat[AssessmentCentrePassMarkSettingsPersistence] = AssessmentCentrePassMarkSettingsPersistence.jsonFormat
  val exercisePassMark = PassMarkThreshold(2.0d, 3.0d)
  val overallPassMark = PassMarkThreshold(2.0d, 14.0d)
  val assessmentCentrePassMarkThresholds = AssessmentCentreExercisePassMarkThresholds(
    exercisePassMark, exercisePassMark, exercisePassMark, overallPassMark
  )
  val assessmentCentrePassMarks = List(AssessmentCentreExercisePassMark(Finance, assessmentCentrePassMarkThresholds))
  val passMarkSettings = AssessmentCentrePassMarkSettingsPersistence(assessmentCentrePassMarks, version, createdDate, createdByUser)
  val newOverallPassMark = PassMarkThreshold(2.0d, 16.0d)
  val newPassMarkThresholds = AssessmentCentreExercisePassMarkThresholds(
    exercisePassMark, exercisePassMark, exercisePassMark, newOverallPassMark
  )
  val newPassMarks = List(AssessmentCentreExercisePassMark(Finance, newPassMarkThresholds))

  def passMarkSettingsRepo = new AssessmentCentrePassMarkSettingsMongoRepository(mongo)
  val collectionName: String = CollectionNames.ASSESSMENT_CENTRE_PASS_MARK_SETTINGS

  override def copyNewPassMarkSettings(passMarks: AssessmentCentrePassMarkSettingsPersistence,
                                       newPassMarks: List[AssessmentCentreExercisePassMark],
                                       newVersion: String,
                                       newDate: DateTime,
                                       newUser: String): AssessmentCentrePassMarkSettingsPersistence = {
    passMarks.copy(schemes = newPassMarks, newVersion, createdDate.plusDays(1), createdByUser)
  }
}

trait PassMarkRepositoryFixture extends MongoRepositorySpec {
  type T <: PassMarkSettingsPersistence
  type U <: PassMark
  implicit val formatter: Format[T]
  val passMarkSettings: T
  val newPassMarkThresholds: PassMarkThresholds
  val newPassMarks: List[U]
  def passMarkSettingsRepo: PassMarkSettingsRepository[T]

  def copyNewPassMarkSettings(passMarks: T, schemes: List[U], newVersion: String, newDate: DateTime, newUser: String): T

  val collectionName: String
  val version = "version-1"
  val createdDate = DateTime.now(DateTimeZone.UTC)
  val createdByUser = "user-1"

  "Pass-mark-settings collection" should {
    "create indexes for the repository" in {

      val indexes = indexDetails(passMarkSettingsRepo.asInstanceOf[PlayMongoRepository[T]]).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "createDate_1", keys = Seq(("createDate", "Ascending")), unique = true)
        )
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
       createdDate.plusDays(1), createdByUser)

      passMarkSettingsRepo.create(passMarkSettings).futureValue
      passMarkSettingsRepo.create(newPassMarkSettings).futureValue

      val result = passMarkSettingsRepo.getLatestVersion.futureValue.get
      result mustBe newPassMarkSettings
    }

    "not return pass mark settings if none have been saved" in {
      val result = passMarkSettingsRepo.getLatestVersion.futureValue
      result mustBe None
    }
  }
}
