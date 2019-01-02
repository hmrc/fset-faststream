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

package services.onlinetesting.phase1

import config.CubiksGatewayConfig
import model.persisted.CubiksTest

trait Phase1TestSelector {
  val gatewayConfig: CubiksGatewayConfig

  def findFirstSjqTest(tests: List[CubiksTest]): Option[CubiksTest] = tests find (_.scheduleId == sjq)

  def findFirstBqTest(tests: List[CubiksTest]): Option[CubiksTest] = tests find (_.scheduleId == bq)

  private[onlinetesting] def sjq = gatewayConfig.phase1Tests.scheduleIds("sjq")

  private[onlinetesting] def bq = gatewayConfig.phase1Tests.scheduleIds("bq")

}
