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

package model.exchange

import model.persisted.{ CubiksTest, PsiTest }
import org.joda.time.DateTime
import play.api.libs.json.{ Json, OFormat }

case class Phase1TestGroupWithNames(expirationDate: DateTime, activeTests: Map[String, CubiksTest])

object Phase1TestGroupWithNames {
  implicit val phase1TestGroupWithNamesFormat = Json.format[Phase1TestGroupWithNames]
}

case class Phase1TestGroupWithNames2(expirationDate: DateTime, tests: Seq[PsiTest])

object Phase1TestGroupWithNames2 {
  implicit val format: OFormat[Phase1TestGroupWithNames2] = Json.format[Phase1TestGroupWithNames2]
}
