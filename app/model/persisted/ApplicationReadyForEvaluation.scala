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

package model.persisted

import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus._
import model.persisted.phase3tests.LaunchpadTest
import model.{ApplicationRoute, SelectedSchemes}
import play.api.libs.json.{Json, OFormat}

case class ApplicationReadyForEvaluation(
  applicationId: String,
  applicationStatus: ApplicationStatus,
  applicationRoute: ApplicationRoute,
  isGis: Boolean,
  activePsiTests: List[PsiTest],
  activeLaunchpadTest: Option[LaunchpadTest],
  prevPhaseEvaluation: Option[PassmarkEvaluation],
  preferences: SelectedSchemes,
  currentSchemeStatus: List[SchemeEvaluationResult]
) {
  def nonGis: Boolean = !isGis
  def isSdipFaststream: Boolean = applicationRoute == ApplicationRoute.SdipFaststream
}

object ApplicationReadyForEvaluation {
  implicit val applicationReadyForEvaluationFormat: OFormat[ApplicationReadyForEvaluation] = Json.format[ApplicationReadyForEvaluation]
}
