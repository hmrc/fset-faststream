/*
 * Copyright 2017 HM Revenue & Customs
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

import org.joda.time.{ DateTime, Period, PeriodType }
import org.joda.time.format.PeriodFormatterBuilder

trait DurationFormatter {

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
}
