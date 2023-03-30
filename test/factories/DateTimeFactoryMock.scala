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

package factories

import org.joda.time.{DateTime, LocalDate}

import java.time.OffsetDateTime

object DateTimeFactoryMock extends DateTimeFactory {
  val nowLocalTimeZone: DateTime = DateTime.now()
  val nowLocalDate: LocalDate = LocalDate.now()
  val nowLocalTimeZoneJavaTime: OffsetDateTime = OffsetDateTime.now()
  val nowLocalDateJavaTime: java.time.LocalDate = java.time.LocalDate.now()
}
