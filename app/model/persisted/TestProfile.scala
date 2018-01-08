/*
 * Copyright 2018 HM Revenue & Customs
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

import org.joda.time.DateTime

abstract class CubiksTestProfile(
) extends TestProfile[CubiksTest] {
  def hasNotResultReadyToDownloadForAllTestsYet =  activeTests.exists(!_.resultsReadyToDownload)
}

trait TestProfile[T <: Test] {
  def expirationDate: DateTime
  def tests: List[T]
  def activeTests = tests.filter(_.usedForResults)
  def hasNotStartedYet = activeTests.forall(_.startedDateTime.isEmpty)
  def hasNotCompletedYet =  activeTests.exists(_.completedDateTime.isEmpty)
  def evaluation: Option[PassmarkEvaluation]
}

trait Test {
  def usedForResults: Boolean
  def testProvider: String
  def token: String
  def startedDateTime: Option[DateTime]
  def completedDateTime: Option[DateTime]
  def invigilatedAccessCode: Option[String]
}
