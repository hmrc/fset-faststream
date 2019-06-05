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

package common

import model.persisted._
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }

trait Phase1TestConcern {
  type U = CubiksTest
  type T = Phase1TestProfile
  type RichTestGroup = Phase1TestGroupWithUserIds
}

trait Phase1TestConcern2 {
  type U = PsiTest
  type T = Phase1TestProfile2
  type RichTestGroup = Phase1TestGroupWithUserIds2
}

trait Phase2TestConcern {
  type U = CubiksTest
  type T = Phase2TestGroup
  type RichTestGroup = Phase2TestGroupWithAppId
}

trait Phase2TestConcern2 {
  type U = PsiTest
  type T = Phase2TestGroup2
  type RichTestGroup = Phase2TestGroupWithAppId
}

trait Phase3TestConcern {
  type U = LaunchpadTest
  type T = Phase3TestGroup
  type RichTestGroup = Phase3TestGroupWithAppId
}
