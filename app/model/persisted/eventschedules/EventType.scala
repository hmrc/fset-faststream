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

package model.persisted.eventschedules

import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

object EventType extends Enumeration {
  type EventType = Value

  val
  ALL_EVENTS,
  ANNUAL_REDEVELOPMENT_OF_NEW_FSAC,
  ASSESSOR_PAYMENT_FOR_CANCELLED_EVENT,
  ASSESSOR_RUNNING_IN_BOARD,
  DIGITAL_AND_TECHNOLOGY_FSB,
  ECONOMIST_APPLICATION_ASSESSMENT,
  ECONOMIST_ASSESSMENT_CENTRE,
  EDIP_CLOSING_RECEPTION,
  EDIP_FINAL_SELECTION,
  EDIP_OPENING_RECEPTION,
  EDIP_TELEPHONE_INTERVIEW,
  EDIP_WORKSHOP,
  E_TRAY_INVIGILATED,
  FAST_STREAM_ASSESSMENT_CENTRE,
  FCO_APPLICATION_ASSESSMENT,
  FCO_FINAL_SELECTION_BOARD,
  FSAC_DRESS_REHEARSAL,
  GCFS_FSB,
  HOP_FINAL_SELECTION,
  OPERATIONAL_RESEARCH_APPLICATION_ASSESSMENT,
  OPERATIONAL_RESEARCH_ASSESSMENT_CENTRE,
  ORAC_OPEN_DAY,
  ORIENTATION_SESSION,
  PDFS_FSB,
  SDIP_APPLICATION_ASSESSMENT,
  SDIP_CLOSING_RECEPTION,
  SDIP_FINAL_SELECTION,
  SDIP_OPENING_RECEPTION,
  SDIP_TELEPHONE_INTERVIEW,
  SEFS_FINAL_SELECTION,
  SKYPE_INTERVIEW,
  SOCIAL_RESEARCH_APPLICATION_ASSESSMENT,
  SOCIAL_RESEARCH_ASSESSMENT_CENTRE,
  STATISTICIAN_APPLICATION_ASSESSMENT,
  STATISTICIAN_ASSESSMENT_CENTRE,
  STATISTICIAN_ASSESSOR_TRAINING = Value


  implicit val EventTypeFormat = new Format[EventType] {
    override def reads(json: JsValue): JsResult[EventType] = JsSuccess(EventType.withName(json.as[String].toUpperCase))

    override def writes(eventType: EventType): JsValue = JsString(eventType.toString)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, EventType] {
    override def write(eventType: EventType): BSONString = BSON.write(eventType.toString)

    override def read(bson: BSONString): EventType = EventType.withName(bson.value.toUpperCase)
  }
}
