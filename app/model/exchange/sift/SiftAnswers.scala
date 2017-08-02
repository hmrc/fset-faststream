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

package model.exchange.sift

import model.exchange.sift.SiftAnswersStatus.SiftAnswersStatus
import play.api.libs.json._

object SiftAnswersStatus extends Enumeration {
  type SiftAnswersStatus = Value

  val DRAFT, SUBMITTED = Value

  implicit val siftAnswersStatusFormat = new Format[SiftAnswersStatus] {
    def reads(json: JsValue) = JsSuccess(SiftAnswersStatus.withName(json.as[String]))
    def writes(myEnum: SiftAnswersStatus) = JsString(myEnum.toString)
  }
}

case class SiftAnswers(
  applicationId: String,
  status: SiftAnswersStatus,
  generalAnswers: Option[GeneralQuestionsAnswers],
  schemeAnswers: Map[String, SchemeSpecificAnswer])

object SiftAnswers {
  implicit val siftAnswersFormat = Json.format[SiftAnswers]

  def apply(a: model.persisted.sift.SiftAnswers): SiftAnswers = {
    SiftAnswers(
      a.applicationId,
      model.exchange.sift.SiftAnswersStatus.withName(a.status.toString),
      a.generalAnswers.map(model.exchange.sift.GeneralQuestionsAnswers(_)),
      a.schemeAnswers.map{
        case (k: String, v: model.persisted.sift.SchemeSpecificAnswer) => (k, model.exchange.sift.SchemeSpecificAnswer(v.rawText))
      }
    )
  }
}
