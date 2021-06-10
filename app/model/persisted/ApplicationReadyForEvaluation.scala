/*
 * Copyright 2021 HM Revenue & Customs
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
import model.{ ApplicationRoute, SelectedSchemes }
import model.persisted.phase3tests.LaunchpadTest
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, __}

//TODO: mongo it is not the intention to read directly from mongo into this case class via the codecs mechanism
// it is more a case of processing the document manually and populate a new instance of this case class manually with the data items read
case class ApplicationReadyForEvaluation(
  applicationId: String,
  applicationStatus: ApplicationStatus,
  applicationRoute: ApplicationRoute,
  isGis: Boolean,
  activePsiTests: List[PsiTest],
  activeLaunchpadTest: Option[LaunchpadTest],
  prevPhaseEvaluation: Option[PassmarkEvaluation],
  preferences: SelectedSchemes
) {
  def nonGis = !isGis
  def isSdipFaststream = applicationRoute == ApplicationRoute.SdipFaststream
}

object ApplicationReadyForEvaluation {
  implicit val applicationReadyForEvaluationFormats = Json.format[ApplicationReadyForEvaluation]
}

case class ReadApplicationReadyForEvaluation(
  applicationId: String,
  applicationStatus: ApplicationStatus,
  applicationRoute: ApplicationRoute,
  assistanceDetails: AssistanceDetails,
  activePsiTests: List[PsiTest],
//  activeLaunchpadTest: Option[LaunchpadTest]//,
//  prevPhaseEvaluation: Option[PassmarkEvaluation],
  preferences: SelectedSchemes
) {
  def isGis = assistanceDetails.guaranteedInterview.contains(true)
}

object ReadApplicationReadyForEvaluation {
  implicit val readApplicationReadyForEvaluationFormat = Json.format[ReadApplicationReadyForEvaluation]

  // Provide an explicit mongo format here to deal with the sub-document root
  // This data lives in the application collection
  val mongoFormat: Format[ReadApplicationReadyForEvaluation] = (
    (__ \ "applicationId").format[String] and
      (__ \ "applicationStatus").format[ApplicationStatus] and
      (__ \ "applicationRoute").format[ApplicationRoute] and
      (__ \ AssistanceDetails.root).format[AssistanceDetails] and
      (__ \ "testGroups" \ "PHASE2" \ "tests").format[List[PsiTest]] and //TODO: mongo the problem here is PHASE is hardcoded
//      (__ \ "testGroups" \ "PHASE3" \ "test").formatNullable[LaunchpadTest] //TODO: mongo the problem here is PHASE is hardcoded
      (__ \ SelectedSchemes.root).format[SelectedSchemes]

    )(ReadApplicationReadyForEvaluation.apply, unlift(ReadApplicationReadyForEvaluation.unapply))
}
