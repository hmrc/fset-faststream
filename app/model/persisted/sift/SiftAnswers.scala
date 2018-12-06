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

package model.persisted.sift

import model.persisted.sift.SiftAnswersStatus.SiftAnswersStatus
import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONDocument, BSONElement, BSONHandler, BSONString, Macros }

import scala.util.Try

object SiftAnswersStatus extends Enumeration {
  type SiftAnswersStatus = Value

  val DRAFT, SUBMITTED = Value

  implicit val SiftAnswersStatusFormat = new Format[SiftAnswersStatus] {
    override def reads(json: JsValue): JsResult[SiftAnswersStatus] = JsSuccess(SiftAnswersStatus.withName(json.as[String].toUpperCase))
    override def writes(eventType: SiftAnswersStatus): JsValue = JsString(eventType.toString)
  }

  implicit object SiftAnswersStatusHandler extends BSONHandler[BSONString, SiftAnswersStatus] {
    override def write(eventType: SiftAnswersStatus): BSONString = BSON.write(eventType.toString)
    override def read(bson: BSONString): SiftAnswersStatus = SiftAnswersStatus.withName(bson.value.toUpperCase)
  }
}

case class SiftAnswers(applicationId: String,
  status: SiftAnswersStatus,
  generalAnswers: Option[GeneralQuestionsAnswers],
  schemeAnswers: Map[String, SchemeSpecificAnswer])

//TODO: Ian mongo 3.2 -> 3.4
object SiftAnswers
{
  implicit object siftAnswersMapHandler extends BSONHandler[BSONDocument, Map[String, SchemeSpecificAnswer]] {

    override def read(bson: BSONDocument): Map[String, SchemeSpecificAnswer] = {
      bson.elements.map { bsonElement =>
        bsonElement.name -> SchemeSpecificAnswer.schemeSpecificAnswerHandler.read(bsonElement.value.asInstanceOf[BSONDocument])
      }.toMap
    }

    override def write(t: Map[String, SchemeSpecificAnswer]): BSONDocument = {
      val stream: Stream[Try[BSONElement]] = t.map {
        case (key, value) => Try(BSONElement(key, SchemeSpecificAnswer.schemeSpecificAnswerHandler.write(value)))
      }.toStream
      BSONDocument(stream)
    }
  }

  implicit val siftAnswersFormat = Json.format[SiftAnswers]
  implicit val siftAnswersHandler = Macros.handler[SiftAnswers]
}
