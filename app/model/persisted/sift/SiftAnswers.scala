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

package model.persisted.sift

import model.persisted.sift.SiftAnswersStatus.SiftAnswersStatus
import org.mongodb.scala.bson.BsonValue
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.Codecs

object SiftAnswersStatus extends Enumeration {
  type SiftAnswersStatus = Value

  val DRAFT, SUBMITTED = Value

  implicit val SiftAnswersStatusFormat = new Format[SiftAnswersStatus] {
    override def reads(json: JsValue): JsResult[SiftAnswersStatus] = JsSuccess(SiftAnswersStatus.withName(json.as[String].toUpperCase))
    override def writes(eventType: SiftAnswersStatus): JsValue = JsString(eventType.toString)
  }

  implicit class BsonOps(val status: SiftAnswersStatus) extends AnyVal {
    def toBson: BsonValue = Codecs.toBson(status)
  }
}

case class SiftAnswers(applicationId: String,
  status: SiftAnswersStatus,
  generalAnswers: Option[GeneralQuestionsAnswers],
  schemeAnswers: Map[String, SchemeSpecificAnswer])

object SiftAnswers {
  implicit val siftAnswersFormat = Json.format[SiftAnswers]
}
