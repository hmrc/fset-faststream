/*
 * Copyright 2016 HM Revenue & Customs
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

package models.page

import models.Adjustments
import org.joda.time.format.{ DateTimeFormatterBuilder, PeriodFormatterBuilder }
import org.joda.time.{ DateTime, Period, PeriodType }

case class Phase3TestsPage(
                            expirationDate: DateTime,
                            started: Boolean,
                            completed: Boolean,
                            adjustments: Option[Adjustments]
                          ) {

  def isStarted: Boolean = started

  def isCompleted: Boolean = completed

  def getDuration: String = {

    val now = DateTime.now
    val date = expirationDate

    val period = new Period(now, date).normalizedStandard(PeriodType.dayTime())

    val periodFormat = new PeriodFormatterBuilder()
      .printZeroAlways()
      .appendDays()
      .appendSuffix(" day ", " days ")
      .appendSeparator(" and ")
      .appendHours()
      .appendSuffix(" hour ", " hours ")
      .toFormatter

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

  def isInvigilatedVideoApproved = adjustments exists (_.isInvigilatedVideoApproved)
}

object Phase3TestsPage {

  def apply(profile: connectors.exchange.Phase3TestGroup, adjustments: Option[Adjustments]): Phase3TestsPage = {
    Phase3TestsPage(
      expirationDate = profile.expirationDate,
      started = profile.activeTests.headOption.exists(_.started),
      completed = profile.activeTests.headOption.exists(_.completed),
      adjustments = adjustments
    )
  }
}
