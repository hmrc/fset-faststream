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

package factories

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, OffsetDateTime, ZoneId}

object ITDateTimeFactoryMock extends DateTimeFactory {
  // Create the date truncated to milliseconds as that is the precision of the date stored in mongo
  // and the comparison will work when we fetch the date back from the db and check it
  def nowLocalTimeZone: OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))truncatedTo(ChronoUnit.MILLIS)

  def nowLocalDate: LocalDate = LocalDate.now
}
