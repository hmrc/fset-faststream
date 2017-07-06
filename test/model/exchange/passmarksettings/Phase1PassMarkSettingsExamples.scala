/*
 * Copyright 2017 HM Revenue & Customs
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

import java.util.UUID

import model.SchemeType.{ SchemeType, _ }
import org.joda.time.DateTime

object Phase1PassMarkSettingsExamples {
  def passmark(implicit now: DateTime) = Phase1PassMarkSettings(List(), "version", now, "userId")

  def passMarkSettings(schemes: List[(SchemeType, Double, Double)])(implicit now: DateTime) =
    Phase1PassMarkSettings(
      schemes.map { case (s, fail, pass) => createPhase1PassMark(s, fail, pass) },
      UUID.randomUUID().toString, now, "userId"
    )

  def createPhase1PassMark(schemeName: SchemeType, fail: Double, pass: Double) = {
    Phase1PassMark(schemeName, Phase1PassMarkThresholds(PassMarkThreshold(fail, pass), PassMarkThreshold(fail, pass)))
  }

}
