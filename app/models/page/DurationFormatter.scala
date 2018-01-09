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

package models.page

import helpers.Timezones
import org.joda.time.format.{ DateTimeFormatterBuilder, PeriodFormatterBuilder }
import org.joda.time.{ DateTime, Period, PeriodType }

trait DurationFormatter {

  def expirationDate: DateTime

  private[page] def now = DateTime.now

  def durationFromNow(date: DateTime): String = {
    val period = new Period(now, date).normalizedStandard(PeriodType.yearMonthDayTime())
    val periodFormat = new PeriodFormatterBuilder()
      .appendYears()
      .appendSuffix(" year", " years")
      .appendSeparator(", ")
      .appendMonths()
      .appendSuffix(" month", " months")
      .appendSeparator(", ")
      .printZeroAlways()
      .appendDays()
      .appendSuffix(" day", " days")
      .appendSeparator(" and ")
      .appendHours()
      .appendSuffix(" hour", " hours")
      .toFormatter

    periodFormat print period
  }

  def getDuration: String = durationFromNow(expirationDate)

  private val expireTimeFormatter = new DateTimeFormatterBuilder()
      .appendClockhourOfHalfday(1)
      .appendLiteral(":")
      .appendMinuteOfHour(2)
      .appendHalfdayOfDayText()

  private val expireDateFormatter =  new DateTimeFormatterBuilder()
      .appendDayOfMonth(1)
      .appendLiteral(" ")
      .appendMonthOfYearText()
      .appendLiteral(" ")
      .appendYear(4, 4)

  private val expirationDateLondon = expirationDate.withZone(Timezones.londonDateTimezone)

  private def timeFormat(dateTime: DateTime) = expireTimeFormatter.toFormatter.print(dateTime).toLowerCase

  private def dateFormat(dateTime: DateTime) = expireDateFormatter.toFormatter.print(dateTime)

  def getExpireTime: String = timeFormat(expirationDate)

  def getExpireTimeLondon: String = timeFormat(expirationDateLondon)

  def getExpireDateTime: String = expireTimeFormatter.append(expireDateFormatter.toFormatter).toFormatter.print(expirationDate)

  def getExpireDate: String = dateFormat(expirationDate)

  def getExpireDateLondon: String = dateFormat(expirationDateLondon)
}
