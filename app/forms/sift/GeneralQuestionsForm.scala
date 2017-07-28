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

package forms.sift

import connectors.exchange.sift.{ DegreeInfoAnswers, GeneralQuestionsAnswers }
import play.api.data.Forms._
import play.api.data.{ Form, FormError }
import play.api.data.format.Formatter

object DegreeInfoForm {

  val form = Form(
    mapping("name" -> text,
      "classification" -> text,
      "graduationYear" -> text,
      "moduleDetails" -> text
  )(DegreeInfoAnswers.apply)(DegreeInfoAnswers.unapply))
}


object GeneralQuestionsForm {
  val form = Form(
    mapping("multiplePassports" -> boolean,
      "passportCountry" -> text,
      "undergradDegree" -> of(degreeInfoFormFormatter),
      "postgradDegree" -> of(degreeInfoFormFormatter)
  )(GeneralQuestionsAnswers.apply)(GeneralQuestionsAnswers.unapply))

  def degreeInfoFormFormatter = new Formatter[Option[DegreeInfoAnswers]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[DegreeInfoAnswers]] = {
        DegreeInfoForm.form.mapping.bind(data) match {
          case Right(success) => Right(Some(success))
          case Left(error) => Left(error)
        }
      }

    override def unbind(key: String, fastPassData: Option[DegreeInfoAnswers]): Map[String, String] =
      fastPassData.map(fpd => DegreeInfoForm.form.fill(fpd).data).getOrElse(Map(key -> ""))
  }
}
