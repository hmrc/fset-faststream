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

package model.persisted

import model.persisted.phase3tests.Phase3TestGroup
import reactivemongo.bson.Macros

trait TestGroupWithIds {
  type U <: Test
  type T <: TestProfile[U]
  def applicationId: String
  def testGroup: T
}

case class Phase1TestWithUserIds(applicationId: String,
  userId: String,
  testGroup: Phase1TestProfile
) extends TestGroupWithIds

object Phase1TestWithUserIds {
  implicit val phase1TestProfileWithAppIdHandler = Macros.handler[Phase1TestWithUserIds]
}

case class Phase2TestGroupWithAppId(applicationId: String,
  testGroup: Phase2TestGroup
) extends TestGroupWithIds

object Phase2TestGroupWithAppId {
  implicit val phase1TestProfileWithAppIdHandler = Macros.handler[Phase2TestGroupWithAppId]
}

case class Phase3TestGroupWithAppId(applicationId: String,
  testGroup: Phase3TestGroup
)
