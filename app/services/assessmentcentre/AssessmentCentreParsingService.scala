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

package services.assessmentcentre

import common.FutureEx
import factories.UUIDFactory
import model.persisted.assessmentcentre.{ Event, EventType }
import org.joda.time.format.DateTimeFormat
import org.joda.time.LocalDate
import play.api.Play
import resource._

import scala.concurrent.Future
import scala.util.{ Failure, Try }
import scala.concurrent.ExecutionContext.Implicits.global

object AssessmentCentreParsingService extends AssessmentCentreParsingService {
  override def fileContents: Future[List[String]] = Future.successful {
    val input = managed(Play.current.resourceAsStream("fset-faststream-event-schedule").get)
    input.acquireAndGet(file => scala.io.Source.fromInputStream(file).getLines().toList.tail)
  }
}

trait AssessmentCentreParsingService {

  private val skillsIdxTable = List(
    "ASSESSOR" -> 9,
    "CHAIR" -> 10,
    "DEPARTMENTAL_ASSESSOR" -> 11,
    "EXERCISE_MARKER" -> 12,
    "QUALITY_ASSURANCE_COORDINATOR" -> 13,
    "SIFTER" -> 14
  )

  def fileContents: Future[List[String]]

  private def stringToRequirement(strRequirement: String): Int =
    strRequirement match {
      case s if s.isEmpty => 0 // no value means 0 requirement
      case s => s.toInt
    }

  def processCentres(): Future[List[Event]] = {
    val df = DateTimeFormat.forPattern("HH:mm")

    fileContents.flatMap { centres =>

      FutureEx.traverseSerial(centres.zipWithIndex) {
        case (line, idx) =>
          val tryRes = Try {
            val items = line.split(", ?", -1)
            val eventType = EventType.withName(items.head.replaceAll("\\s", "_").toUpperCase)
            val location = items(1)
            val venue = items(2)
            val date = LocalDate.parse(items(3), DateTimeFormat.forPattern("dd/MM/yy"))
            val startTime = df.parseLocalTime(items(4))
            val endTime = df.parseLocalTime(items(5))
            val capacity = items(6).toInt
            val minViableAttendees = items(7).toInt
            val attendeeSafetyMargin = items(8).toInt

            val skillRequirements: Map[String, Int] =
              skillsIdxTable.map {
                case (skill, skillIdx) => skill -> stringToRequirement(items(skillIdx))
              }.toMap

            Event(
              id = UUIDFactory.generateUUID(),
              eventType = eventType,
              location = location,
              venue = venue,
              date = date,
              startTime = startTime,
              endTime = endTime,
              capacity = capacity,
              minViableAttendees = minViableAttendees,
              attendeeSafetyMargin = attendeeSafetyMargin,
              skillRequirements = skillRequirements)
          }.recoverWith {
            case ex =>
              Failure(new Exception(s"Error on L${idx + 1} of the CSV. ${ex.getMessage}. ${ex.getClass.getCanonicalName}"))
          }
          Future.fromTry(tryRes)
      }
    }
  }
}
