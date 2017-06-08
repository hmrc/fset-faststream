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

package model.persisted.assessmentcentre
import model.persisted.assessmentcentre.SkillType.SkillType
import repositories.{ BSONDateTimeHandler, BSONLocalDateHandler, BSONMapHandler, BSONMapSkillTypeHandler }
import org.joda.time.{ DateTime, LocalDate }
import reactivemongo.bson.Macros

/**
  * Created by fayimora on 08/06/2017.
  */


case class Event(eventType: String,
                 date: LocalDate,
                 capacity: Int,
                 minViableAttendees: Int,
                 attendeeSafetyMargin: Int,
                 startTime: DateTime,
                 endTime: DateTime,
                 skillRequirements: Map[SkillType, Int])

object Event {
  implicit val eventHandler = Macros.handler[Event]
}
