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

package model.persisted

import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import model.persisted.sift.SiftTestGroup
import reactivemongo.bson.Macros

trait TestGroupWithIds[U <: Test, T <: TestProfile[U]] {
  def applicationId: String
  def testGroup: T
}

case class Phase1TestGroupWithUserIds(
  applicationId: String,
  userId: String,
  testGroup: Phase1TestProfile
) extends TestGroupWithIds[CubiksTest, Phase1TestProfile]

object Phase1TestGroupWithUserIds {
  implicit val phase1TestProfileWithAppIdHandler = Macros.handler[Phase1TestGroupWithUserIds]
}

case class Phase1TestGroupWithUserIds2(
  applicationId: String,
  userId: String,
  testGroup: Phase1TestProfile2
) extends TestGroupWithIds[PsiTest, Phase1TestProfile2]

object Phase1TestGroupWithUserIds2 {
  implicit val phase1TestProfileWithAppIdHandler = Macros.handler[Phase1TestGroupWithUserIds2]
}

case class Phase2TestGroupWithAppId(applicationId: String,
  testGroup: Phase2TestGroup
) extends TestGroupWithIds[CubiksTest, Phase2TestGroup]

object Phase2TestGroupWithAppId {
  implicit val phase2TestGroupWithAppIdHandler = Macros.handler[Phase2TestGroupWithAppId]
}

case class Phase2TestGroupWithAppId2(applicationId: String,
                                    testGroup: Phase2TestGroup2) extends TestGroupWithIds[PsiTest, Phase2TestGroup2]

object Phase2TestGroupWithAppId2 {
  implicit val phase2TestGroupWithAppIdHandler = Macros.handler[Phase2TestGroupWithAppId2]
}


case class Phase3TestGroupWithAppId(
  applicationId: String,
  testGroup: Phase3TestGroup
) extends TestGroupWithIds[LaunchpadTest, Phase3TestGroup]

object Phase3TestGroupWithAppId {
  implicit val phase3TestGroupWithAppIdHandler = Macros.handler[Phase3TestGroupWithAppId]
}
