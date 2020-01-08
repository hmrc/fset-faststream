/*
 * Copyright 2020 HM Revenue & Customs
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

package persisted

import model.FsbType

object FsbTypeExamples {
  val YamlFsbTypes = List(
    FsbType("SAC"),
    FsbType("SRAC"),
    FsbType("ORAC"),
    FsbType("EAC_DC"),
    FsbType("GOV COMS"),
    FsbType("DAT"),
    FsbType("SEFS"),
    FsbType("FCO"),
    FsbType("P&D"),
    FsbType("FIFS"),
    FsbType("COMMERCIAL"),
    FsbType("HOP")
  )
}
