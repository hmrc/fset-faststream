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

package model.persisted.sift

import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class UnderGradDegreeInfoAnswers(
  name: String,
  classification: String,
  graduationYear: String,
  moduleDetails: Option[String]
)

object UnderGradDegreeInfoAnswers {
  implicit val undergradDegreeInfoAnswersFormat= Json.format[UnderGradDegreeInfoAnswers]
  implicit val undergradDegreeInfoAnswersHandler = Macros.handler[UnderGradDegreeInfoAnswers]

  def apply(o: model.exchange.sift.UndergradDegreeInfoAnswers): UnderGradDegreeInfoAnswers = UnderGradDegreeInfoAnswers(
    o.name, o.classification, o.graduationYear, o.moduleDetails
  )
}

case class PostGradDegreeInfoAnswers(
  name: String,
  graduationYear: String,
  otherDetails: Option[String],
  projectDetails: Option[String]
)

object PostGradDegreeInfoAnswers {
  implicit val postGradDegreeInfoAnswersFormat = Json.format[PostGradDegreeInfoAnswers]
  implicit val postGradDegreeInfoAnswersHandler = Macros.handler[PostGradDegreeInfoAnswers]

  def apply(o: model.exchange.sift.PostGradDegreeInfoAnswers): PostGradDegreeInfoAnswers = PostGradDegreeInfoAnswers(
    o.name, o.graduationYear, o.otherDetails, o.projectDetails
  )
}

case class GeneralQuestionsAnswers(
  multiplePassports: Boolean,
  secondPassportCountry: Option[String],
  passportCountry: String,
  undergradDegree: Option[UnderGradDegreeInfoAnswers],
  postgradDegree: Option[PostGradDegreeInfoAnswers]
)

object GeneralQuestionsAnswers {
  implicit val generalQuestionsAnswersFormat = Json.format[GeneralQuestionsAnswers]
  implicit val generalQuestionsAnswersHandler = Macros.handler[GeneralQuestionsAnswers]

  def apply(o: model.exchange.sift.GeneralQuestionsAnswers): GeneralQuestionsAnswers = GeneralQuestionsAnswers(
    o.multiplePassports, o.secondPassportCountry, o.passportCountry, o.undergradDegree.map(UnderGradDegreeInfoAnswers.apply),
    o.postgradDegree.map(PostGradDegreeInfoAnswers.apply)
  )
}
