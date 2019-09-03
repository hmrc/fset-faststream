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

import models.Adjustments
import org.joda.time.DateTime

case class Phase2TestsPage2(expirationDate: DateTime,
                            tests: Seq[PsiTestPage],
                            adjustments: Option[Adjustments]) extends DurationFormatter {

//  def isStarted: Boolean = tests.exists(_.started)
//  def isCompleted: Boolean = tests.exists(_.completed)
  def areStarted: Boolean = tests.exists(_.started)
  def allCompleted: Boolean = tests.forall(_.completed)

  def isInvigilatedETrayApproved: Boolean = adjustments exists (_.isInvigilatedETrayApproved)
}

object Phase2TestsPage2 {

  def apply(profile: connectors.exchange.Phase2TestGroupWithActiveTest2,
            adjustments: Option[Adjustments]): Phase2TestsPage2 = {
    Phase2TestsPage2(
      expirationDate = profile.expirationDate,
      tests = profile.activeTests.map(PsiTestPage.apply),
      adjustments = adjustments
    )
  }
}
