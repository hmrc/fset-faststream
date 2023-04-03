/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.OffsetDateTime

abstract class PsiTestProfile() extends TestProfile[PsiTest] {
  def hasNotResultReadyToDownloadForAllTestsYet: Boolean = activeTests.exists(!_.resultsReadyToDownload)
}

trait TestProfile[T <: Test] {
  def expirationDate: OffsetDateTime
  def tests: List[T]
  def activeTests = tests.filter(_.usedForResults)
  def hasNotStartedYet = activeTests.forall(_.startedDateTime.isEmpty)
  def hasNotCompletedYet =  activeTests.exists(_.completedDateTime.isEmpty)
  def evaluation: Option[PassmarkEvaluation]
}

//TODO: look to see if we still need all members of this trait
trait Test {
  def usedForResults: Boolean
  def testProvider: String
  def startedDateTime: Option[OffsetDateTime]
  def completedDateTime: Option[OffsetDateTime]
  def invigilatedAccessCode: Option[String]
}
