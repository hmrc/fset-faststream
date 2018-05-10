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

package connectors.exchange.sift

import org.joda.time._
import org.joda.time.format.PeriodFormatterBuilder
import play.api.libs.json.Json

case class SiftState(siftEnteredDate: DateTime, expirationDate: DateTime) {

  def expiryDateDurationRemaining: String = durationFromNow(expirationDate)

  private def durationFromNow(date: DateTime): String = {
    val now = DateTime.now
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
      .appendSeparator(" and ")
      .appendMinutes()
      .appendSuffix(" minute", " minutes")
      .toFormatter

    periodFormat print period
  }
}

object SiftState {
  implicit val siftStateFormat = Json.format[SiftState]
}
