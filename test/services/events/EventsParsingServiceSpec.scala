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

package services.events

import model.persisted.eventschedules.{ Event, EventType }
import services.BaseServiceSpec

import scala.concurrent.Future

class EventsParsingServiceSpec extends BaseServiceSpec {
  "processCentres" must {
    "successfully saves and loads the file contents" in new GoodTestFixture {
      val events: Seq[Event] = service.processCentres().futureValue

      events.size mustBe 2
      events.head.eventType mustBe EventType.FSAC
      events(1).skillRequirements.getOrElse("QUALITY_ASSURANCE_COORDINATOR", "--") mustBe 0
    }

    "fails gracefully when any field is malformed" in new MalformedTestFixture {
      an[Exception] must be thrownBy service.processCentres().futureValue
    }
  }


  trait MalformedTestFixture {
    val service = new EventsParsingService {
      val fileContents: Future[List[String]] = Future.successful(List(
        "fsac,london,london fsac,03/04/17,09:0,12:00,36,4,5,6,1,1,1,1,2", // malformed starttime
        "telephone interview,london,virtual,,08:00,13:30,36,24,7,,,,,," // missing date
      ))
    }
  }

  trait GoodTestFixture {
    val service = new EventsParsingService {
      val fileContents: Future[List[String]] = Future.successful(List(
        "fsac,London,london fsac,03/04/17,09:00,12:00,36,4,5,6,1,1,1,1,2",
        "telephone interview,London,virtual,04/04/17,08:00,13:30,36,24,7,,,,,,"
      ))
    }
  }
}
