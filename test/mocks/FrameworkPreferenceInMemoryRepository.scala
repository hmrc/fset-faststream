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

import model.{ Alternatives, LocationPreference, Preferences }
import repositories.FrameworkPreferenceRepository

import scala.concurrent.Future

object FrameworkPreferenceInMemoryRepository extends FrameworkPreferenceRepository with InMemoryStorage[Preferences] {
  // Seed with test data.
  inMemoryRepo +=
    "111-111" ->
    Preferences(
      LocationPreference("Winter", "Wonder", "Land", None),
      None,
      Some(true),
      Some(Alternatives(location = true, framework = true))
    )

  override def savePreferences(applicationId: String, preferences: Preferences): Future[Unit] =
    update(applicationId, preferences)

  override def tryGetPreferences(applicationId: String): Future[Option[Preferences]] =
    Future.successful(inMemoryRepo.get(applicationId))

  override def notFound(applicationId: String): Preferences = throw new RuntimeException
}
