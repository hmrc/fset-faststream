/*
 * Copyright 2022 HM Revenue & Customs
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

package models

import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import security.Roles.WithdrawnApplicationRole

object ConsiderMeForSdipHelper {

  def faststreamerNotEligibleForSdip(cachedData: CachedData)(implicit request: RequestHeader,
                                     messages: Messages):PartialFunction[Option[ApplicationData], String] = {
    case Some(app) if WithdrawnApplicationRole.isAuthorized(cachedData) => Messages("error.faststream.becomes.sdip.withdrew")
    case Some(app) if !app.progress.submitted => Messages("error.faststream.becomes.sdip.not.submitted")
    case Some(app) if app.progress.phase1TestProgress.phase1TestsExpired && !app.progress.phase1TestProgress.phase1TestsPassed
      && !app.progress.phase1TestProgress.phase1TestsFailed => Messages("error.faststream.becomes.sdip.test.expired")
    case None => Messages("error.faststream.becomes.sdip.not.submitted")
  }

  def convertToArchiveEmail(email: String) = {
    val emailParts = email.split("@").toList
    s"${emailParts.head}-old-${emailParts(1)}@localhost"
  }
}
