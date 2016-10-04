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

package mocks

import model.Commands.PassMarkSettingsCreateResponse
import model.SchemeType._
import model.exchange.passmarksettings.{ PassMarkThreshold, SchemePassMark, SchemePassMarkSettings, SchemePassMarkThresholds }
import org.joda.time.DateTime
import repositories.PassMarkSettingsRepository

import scala.concurrent.Future

object PassMarkSettingsInMemoryRepository extends PassMarkSettingsInMemoryRepository

class PassMarkSettingsInMemoryRepository extends PassMarkSettingsRepository {
  val mockSchemes = SchemePassMark(
    Finance,
    SchemePassMarkThresholds(
      behavioural = PassMarkThreshold(20.0, 80.0),
      situational = PassMarkThreshold(20.0, 80.0)
    )
  ) :: SchemePassMark(
    Commercial,
    SchemePassMarkThresholds(
      behavioural = PassMarkThreshold(20.0, 80.0),
      situational = PassMarkThreshold(20.0, 80.0)
    )
  ) :: SchemePassMark(
    DigitalAndTechnology,
    SchemePassMarkThresholds(
      behavioural = PassMarkThreshold(20.0, 80.0),
      situational = PassMarkThreshold(20.0, 80.0)
    )
  ) :: SchemePassMark(
    Finance,
    SchemePassMarkThresholds(
      behavioural = PassMarkThreshold(20.0, 80.0),
      situational = PassMarkThreshold(20.0, 80.0)
    )
  ) :: SchemePassMark(
    ProjectDelivery,
    SchemePassMarkThresholds(
      behavioural = PassMarkThreshold(20.0, 80.0),
      situational = PassMarkThreshold(20.0, 80.0)
    )
  ) :: Nil

  override def create(passMarkSettings: SchemePassMarkSettings): Future[PassMarkSettingsCreateResponse] = ???

  override def tryGetLatestVersion: Future[Option[SchemePassMarkSettings]] = {
    val currentVersion = "Version-UUID-like-string"
    val createdByUser = "User-UUID-like-string"
    val setting = "location1Scheme1"

    Future.successful(Some(SchemePassMarkSettings(mockSchemes, currentVersion, new DateTime(),
      createdByUser, setting)))
  }
}
