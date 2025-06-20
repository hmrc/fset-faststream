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

package model

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, OFormat, __}

case class SelectedSchemes(schemes: List[SchemeId], orderAgreed: Boolean, eligible: Boolean)

object SelectedSchemes {
  implicit val selectedSchemesFormat: OFormat[SelectedSchemes] = Json.format[SelectedSchemes]

  // Provide an explicit mongo format here to deal with the sub-document root
  val root = "scheme-preferences"
  val mongoFormat: Format[SelectedSchemes] = (
    (__ \ root \ "schemes").format[List[SchemeId]] and
      (__ \ root \ "orderAgreed").format[Boolean] and
      (__ \ root \ "eligible").format[Boolean]
    )(SelectedSchemes.apply, unlift(o => Some(Tuple.fromProductTyped(o))))
}
