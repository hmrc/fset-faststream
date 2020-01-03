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

package services.onlinetesting.phase2

import config.TestIntegrationGatewayConfig
import model.persisted.PsiTest

trait Phase2TestSelector2 {
  val gatewayConfig: TestIntegrationGatewayConfig //TODO: use p2 config instead

  def findFirstTest1Test(tests: List[PsiTest]): Option[PsiTest] = tests find (_.inventoryId == inventoryIdForTest("test1"))

  def findFirstTest2Test(tests: List[PsiTest]): Option[PsiTest] = tests find (_.inventoryId == inventoryIdForTest("test2"))

  private[onlinetesting] def inventoryIdForTest(testName: String) = gatewayConfig.phase2Tests.tests(testName).inventoryId
}
