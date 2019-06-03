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

package models.page

import org.joda.time.DateTime

case class Phase1TestsPage2(expirationDate: DateTime,
                            tests: Seq[PsiTestPage]) extends DurationFormatter {

  def areStarted: Boolean = tests.exists(_.started)
  def allCompleted: Boolean = tests.forall(_.completed)
}

object Phase1TestsPage {
  def apply(profile: connectors.exchange.Phase1TestGroupWithNames2): Phase1TestsPage2 = {
    Phase1TestsPage2(
      expirationDate = profile.expirationDate,
      tests = profile.tests.map(PsiTestPage.apply)
    )
  }
}
