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

package model.exchange.passmarksettings

import model.SchemeType
import model.exchange.passmarksettings.Phase1PassMarkSettingsExamples._
import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec

class Phase1PassMarkSettingsSpec extends PlaySpec {
  val pastDate: DateTime = DateTime.now().minusDays(1)
  implicit val now: DateTime = DateTime.now()

  private val faststreamPassMarkToSave = passMarkSettings(List((SchemeType.Commercial, 20.0, 80.0)))(pastDate)
  private val edipPassMarkToSave = passMarkSettings(List((SchemeType.Edip, 20.0, 80.0)))
  private val sdipPassMarkToSave = passMarkSettings(List((SchemeType.Sdip, 20.0, 80.0)))
  private val allPassmarks = passMarkSettings(List(
    (SchemeType.Commercial, 30.0, 70.0),
    (SchemeType.Edip, 30.0, 70.0),
    (SchemeType.Sdip, 40.0, 60.0))
  )

  "phase1 pass mark settings merge" should {
    "merge None with the new passmark" in {
      val merged = Phase1PassMarkSettings.merge(
        latestPassMarkSettings = None,
        newestPassMarkSettings = faststreamPassMarkToSave
      )
      merged mustBe faststreamPassMarkToSave
      merged.version mustBe faststreamPassMarkToSave.version
    }

    "merge two disjoint passmarks" in {
      val merged = Phase1PassMarkSettings.merge(
        latestPassMarkSettings = Some(faststreamPassMarkToSave),
        newestPassMarkSettings = edipPassMarkToSave
      )
      merged.schemes mustBe List(
        createPhase1PassMark(SchemeType.Commercial, 20.0, 80.0),
        createPhase1PassMark(SchemeType.Edip, 20.0, 80.0)
      )
      merged.createDate mustBe now
      merged.version mustBe edipPassMarkToSave.version
    }

    "merge two passmarks and update already saved settings" in {
      val merged = Phase1PassMarkSettings.merge(
        latestPassMarkSettings = Some(faststreamPassMarkToSave),
        newestPassMarkSettings = allPassmarks
      )
      merged.schemes mustBe List(
        createPhase1PassMark(SchemeType.Commercial, 30.0, 70.0),
        createPhase1PassMark(SchemeType.Edip, 30.0, 70.0),
        createPhase1PassMark(SchemeType.Sdip, 40.0, 60.0)
      )
      merged.createDate mustBe now
      merged.version mustBe allPassmarks.version
    }

  }
}
