/*
 * Copyright 2019 HM Revenue & Customs
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

import connectors.exchange.referencedata.SchemeId
import play.api.mvc.PathBindable

package object controllers {

  object Binders {

    implicit val schemeIdPathBinder = new PathBindable.Parsing[SchemeId](
      parse = (value: String) => SchemeId(value),
      serialize = _.value,
      error = (m: String, e: Exception) => s"Can't parse $m as SchemeId: ${e.getMessage}"
    )
  }
}
