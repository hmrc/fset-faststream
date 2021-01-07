/*
 * Copyright 2021 HM Revenue & Customs
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

package connectors.exchange

import models.UniqueIdentifier
import org.joda.time.{ DateTime, Period }
import org.joda.time.format.{ DateTimeFormatterBuilder, PeriodFormatterBuilder }

abstract class CubiksTestGroup() extends TestGroup[CubiksTest] {
  def hasNotResultReadyToDownloadForAllTestsYet = activeTests.exists(!_.resultsReadyToDownload)
}

trait TestGroup[T <: Test] {
  def expirationDate: DateTime
  def tests: List[T]
  def activeTests = tests filter (_.usedForResults)
  def hasNotStartedYet = activeTests.forall(_.startedDateTime.isEmpty)
  def hasNotCompletedYet = activeTests.exists(_.completedDateTime.isEmpty)

  def getDuration: String = {
    val now = DateTime.now
    val date = expirationDate

    val periodFormat = new PeriodFormatterBuilder()
      .printZeroAlways()
      .appendDays()
      .appendSuffix(" day ", " days ")
      .appendSeparator(" and ")
      .appendHours()
      .appendSuffix(" hour ", " hours ")
      .toFormatter

    val period = new Period(now, date)
    periodFormat print period
  }

  def getExpireDateTime: String = {

    val dateTimeFormat = new DateTimeFormatterBuilder()
      .appendClockhourOfHalfday(1)
      .appendLiteral(":")
      .appendMinuteOfHour(2)
      .appendHalfdayOfDayText()
      .appendLiteral(" on ")
      .appendDayOfMonth(1)
      .appendLiteral(" ")
      .appendMonthOfYearText()
      .appendLiteral(" ")
      .appendYear(4, 4)
      .toFormatter

    dateTimeFormat.print(expirationDate)
  }

  def getExpireDate: String = {

    val dateTimeFormat = new DateTimeFormatterBuilder()
      .appendDayOfMonth(1)
      .appendLiteral(" ")
      .appendMonthOfYearText()
      .appendLiteral(" ")
      .appendYear(4, 4)
      .toFormatter

    dateTimeFormat.print(expirationDate)
  }
}

trait Test {
  def usedForResults: Boolean
  def token: UniqueIdentifier
  def startedDateTime: Option[DateTime]
  def completedDateTime: Option[DateTime]
}
