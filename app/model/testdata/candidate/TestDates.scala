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

package model.testdata.candidate

import org.joda.time.{DateTime, DateTimeZone}

import java.time.OffsetDateTime

trait TestDates {
  def start: Option[OffsetDateTime]

  def expiry: Option[OffsetDateTime]

  def completion: Option[OffsetDateTime]

  def randomDateBeforeNow: DateTime = {
    DateTime.now(DateTimeZone.UTC).minusHours(scala.util.Random.nextInt(120))
  }

  def randomDateAroundNow: DateTime = {
    DateTime.now(DateTimeZone.UTC).plusHours(scala.util.Random.nextInt(240)).minusHours(scala.util.Random.nextInt(240))
  }
}
