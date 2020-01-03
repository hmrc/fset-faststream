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

package services.onlinetesting.phase1

import config.TestIntegrationGatewayConfig
import model.persisted.PsiTest

trait Phase1TestSelector2 {
  val gatewayConfig: TestIntegrationGatewayConfig

  def findFirstTest1Test(tests: List[PsiTest]): Option[PsiTest] = tests find (_.inventoryId == inventoryIdForTest("test1"))

  def findFirstTest2Test(tests: List[PsiTest]): Option[PsiTest] = tests find (_.inventoryId == inventoryIdForTest("test2"))

  def findFirstTest3Test(tests: List[PsiTest]): Option[PsiTest] = tests find (_.inventoryId == inventoryIdForTest("test3"))

  def findFirstTest4Test(tests: List[PsiTest]): Option[PsiTest] = tests find (_.inventoryId == inventoryIdForTest("test4"))

  //TODO: support a list of tests?
  private[onlinetesting] def inventoryIdForTest(testName: String) = gatewayConfig.phase1Tests.tests(testName).inventoryId

}
