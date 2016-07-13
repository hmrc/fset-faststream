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

import connectors.PassMarkExchangeObjects.{Scheme, SchemeThreshold, SchemeThresholds, Settings}
import model.Commands.PassMarkSettingsCreateResponse
import model.Schemes._
import org.joda.time.DateTime
import repositories.PassMarkSettingsRepository

import scala.concurrent.Future

object PassMarkSettingsInMemoryRepository extends PassMarkSettingsInMemoryRepository

class PassMarkSettingsInMemoryRepository extends PassMarkSettingsRepository {
  val mockSchemes = Scheme(
    Business,
    SchemeThresholds(
      competency = SchemeThreshold(20.0, 80.0),
      verbal = SchemeThreshold(20.0, 80.0),
      numerical = SchemeThreshold(20.0, 80.0),
      situational = SchemeThreshold(20.0, 90.0),
      combination = None
    )
  ) :: Scheme(
    Commercial,
    SchemeThresholds(
      competency = SchemeThreshold(20.0, 80.0),
      verbal = SchemeThreshold(20.0, 80.0),
      numerical = SchemeThreshold(20.0, 80.0),
      situational = SchemeThreshold(20.0, 80.0),
      combination = None
    )
  ) :: Scheme(
    DigitalAndTechnology,
    SchemeThresholds(
      competency = SchemeThreshold(20.0, 80.0),
      verbal = SchemeThreshold(20.0, 80.0),
      numerical = SchemeThreshold(20.0, 80.0),
      situational = SchemeThreshold(20.0, 80.0),
      combination = None
    )
  ) :: Scheme(
    Finance,
    SchemeThresholds(
      competency = SchemeThreshold(20.0, 80.0),
      verbal = SchemeThreshold(20.0, 80.0),
      numerical = SchemeThreshold(20.0, 80.0),
      situational = SchemeThreshold(20.0, 80.0),
      combination = None
    )
  ) :: Scheme(
    ProjectDelivery,
    SchemeThresholds(
      competency = SchemeThreshold(20.0, 80.0),
      verbal = SchemeThreshold(20.0, 80.0),
      numerical = SchemeThreshold(20.0, 80.0),
      situational = SchemeThreshold(20.0, 80.0),
      combination = None
    )
  ) :: Scheme(
    "TestSchemeSTEM",
    SchemeThresholds(
      competency = SchemeThreshold(20.0, 80.0),
      verbal = SchemeThreshold(20.0, 80.0),
      numerical = SchemeThreshold(20.0, 80.0),
      situational = SchemeThreshold(20.0, 80.0),
      combination = None
    )
  ) :: Nil

  override def create(settings: Settings, schemeNames: List[String]): Future[PassMarkSettingsCreateResponse] = ???

  override def tryGetLatestVersion(schemeNames: List[String]): Future[Option[Settings]] = {
    val currentVersion = "Version-UUID-like-string"
    val createdByUser = "User-UUID-like-string"
    val setting = "location1Scheme1"

    Future.successful(Some(Settings(mockSchemes, currentVersion, new DateTime(), createdByUser, setting)))
  }
}
