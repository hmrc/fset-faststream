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

package model

import model.persisted.{ Phase1TestProfile, Phase1TestProfile2 }
import org.joda.time.DateTime

object Phase1TestProfileExamples {

  def profile(implicit now: DateTime) = Phase1TestProfile(now, List(Phase1TestExamples.firstTest))

  def psiProfile(implicit now: DateTime) = Phase1TestProfile2(now, List(Phase1TestExamples.firstPsiTest))
}
