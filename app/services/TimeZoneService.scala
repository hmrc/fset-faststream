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

package services

import com.google.inject.ImplementedBy

import java.time.{Instant, LocalDateTime, ZoneId}
import javax.inject.Singleton

@ImplementedBy(classOf[GBTimeZoneService])
trait TimeZoneService {
  def timeZone: ZoneId

  /**
   * Returns a date-time object with a value equal to what the time was, at the given point in time
   * specified by `utcMillis`, as it was recorded in the location/locale specified by `timeZone`.
   */
  def localize(utcMillis: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(utcMillis), timeZone)
}

@Singleton
class GBTimeZoneService extends TimeZoneService {
  override def timeZone: ZoneId = ZoneId.of("Europe/London")
}
