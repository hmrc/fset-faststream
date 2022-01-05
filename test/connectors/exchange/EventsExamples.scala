/*
 * Copyright 2022 HM Revenue & Customs
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

import java.util.UUID

import connectors.events.{ Event, Location, Session, Venue }
import models.UniqueIdentifier
import models.events.EventType
import org.joda.time.{ LocalDate, LocalTime }

object EventsExamples {

  val Event1 = Event(
    UniqueIdentifier(UUID.randomUUID()),
    EventType.FSAC,
    "",
    Location("London"),
    Venue("London FSAC", "London Test FSAC"),
    LocalDate.now,
    10,
    10,
    10,
    LocalTime.now,
    LocalTime.now.plusHours(24),
    Map(),
    List(
      Session(
        UniqueIdentifier(UUID.randomUUID()),
        "TestSession",
        10,
        10,
        10,
        LocalTime.now,
        LocalTime.now.plusHours(12)
      )
    )
  )

}
