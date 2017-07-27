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

package model.persisted

import model.SchemeId
import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONArray, BSONDocument, BSONHandler, BSONString, Macros }

import scala.util.Try

case class SchemeSpecificAnswer(rawText: String)

case class SiftAnswers(applicationId: String, answers: Map[String, SchemeSpecificAnswer])

object SchemeSpecificAnswer
{
  implicit val schemeSpecificAnswerFormat = Json.format[SchemeSpecificAnswer]
  implicit val schemeAnswersHandler = Macros.handler[SchemeSpecificAnswer]
}

object SiftAnswers
{
  implicit object siftAnswersMapHandler extends BSONHandler[BSONDocument, Map[String, SchemeSpecificAnswer]] {
    override def read(bson: BSONDocument): Map[String, SchemeSpecificAnswer] = {
      bson.elements.map {
        case (key, value) => key -> SchemeSpecificAnswer(value.asInstanceOf[BSONString].value)
      }.toMap
    }

    override def write(t: Map[String, SchemeSpecificAnswer]): BSONDocument = {
      val stream: Stream[Try[(String, BSONString)]] = t.map {
        case (key, value) => Try((key, BSONString(value.rawText)))
      }.toStream
      BSONDocument(stream)
    }
  }

  implicit val siftAnswersFormat = Json.format[SiftAnswers]
  implicit val siftAnswersHandler = Macros.handler[SiftAnswers]
}

