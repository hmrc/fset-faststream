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

package model.report.onlinetestpassmark

import model.OnlineTestCommands.{ PsiTestResult, TestResult }
import model.report.TestResultsForOnlineTestPassMarkReportItem

import scala.util.Random

object TestResultsForOnlineTestPassMarkReportItemExamples {

  lazy val testResults1 = newTestResults
  lazy val testResults2 = newTestResults

  private def someDouble = Some(Random.nextDouble())

//  def newTestResult = TestResult("Completed", "Example Norm", someDouble, someDouble, someDouble, someDouble)
  def newTestResult = PsiTestResult("Completed", someDouble.get, someDouble.get)

  def maybe[A](value: => A) = if (Random.nextBoolean()) Some(value) else None

  def newTestResults =
    TestResultsForOnlineTestPassMarkReportItem(
      Seq(maybe(newTestResult)),
      Seq(maybe(newTestResult)),
      Some(VideoInterviewTestResultExamples.Example1),
      maybe(newTestResult),
      None, None, None, None
    )
}
