/*
 * Copyright 2023 HM Revenue & Customs
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

package model.exchange.passmarksettings

import model.Schemes
import model.exchange.passmarksettings.Phase1PassMarkSettingsExamples._
import org.scalatestplus.play.PlaySpec

import java.time.OffsetDateTime

class Phase1PassMarkSettingsSpec extends PlaySpec with Schemes {
  val pastDate: OffsetDateTime = OffsetDateTime.now.minusDays(1)
  implicit val now: OffsetDateTime = OffsetDateTime.now

  private val faststreamPassMarkToSave = passMarkSettings(List((Commercial, 20.0, 80.0)))(pastDate)
  private val edipPassMarkToSave = passMarkSettings(List((Edip, 20.0, 80.0)))
  private val allPassmarks = passMarkSettings(List(
    (Commercial, 30.0, 70.0),
    (Edip, 30.0, 70.0),
    (Sdip, 40.0, 60.0))
  )

  "phase1 pass mark settings merge" should {
    "merge None with the new passmark" in {
      val merged = Phase1PassMarkSettings.merge(
        oldPassMarkSettings = None,
        newPassMarkSettings = faststreamPassMarkToSave
      )
      merged mustBe faststreamPassMarkToSave
    }

    "merge two disjoint passmarks" in {
      val merged = Phase1PassMarkSettings.merge(
        oldPassMarkSettings = Some(faststreamPassMarkToSave),
        newPassMarkSettings = edipPassMarkToSave
      )
      merged.schemes mustBe List(
        createPhase1PassMark(Commercial, 20.0, 80.0),
        createPhase1PassMark(Edip, 20.0, 80.0)
      )
      merged.createDate mustBe now
      merged.version mustBe edipPassMarkToSave.version
    }

    "merge two passmarks and update already saved settings" in {
      val merged = Phase1PassMarkSettings.merge(
        oldPassMarkSettings = Some(faststreamPassMarkToSave),
        newPassMarkSettings = allPassmarks
      )
      merged.schemes mustBe List(
        createPhase1PassMark(Commercial, 30.0, 70.0),
        createPhase1PassMark(Edip, 30.0, 70.0),
        createPhase1PassMark(Sdip, 40.0, 60.0)
      )
      merged.createDate mustBe now
      merged.version mustBe allPassmarks.version
    }

    "merge preserves the list order from the latest and then from newest passmark settings" in {
      val merged = Phase1PassMarkSettings.merge(
        oldPassMarkSettings = Some(passMarkSettings(List(
          (Commercial, 20.0, 80.0),
          (DigitalDataTechnologyAndCyber, 20.0, 80.0),
          (DiplomaticAndDevelopment, 20.0, 80.0)))),
        newPassMarkSettings = passMarkSettings(List(
          (DiplomaticAndDevelopmentEconomics, 20.0, 80.0),
          (GovernmentPolicy, 20.0, 80.0),
          (DiplomaticAndDevelopment, 20.0, 80.0)))
      )
      merged.schemes.map(_.schemeId) mustBe List(
        Commercial,
        DigitalDataTechnologyAndCyber,
        DiplomaticAndDevelopment,
        DiplomaticAndDevelopmentEconomics,
        GovernmentPolicy
      )
    }
  }
}
