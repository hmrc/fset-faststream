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

package forms

import connectors.exchange.{ Answer, Question, Questionnaire }
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages

object ParentalOccupationQuestionnaireForm {

  val employedDependentFormatter = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val check = data.get("employedParent")
      val value = data.get(key).filterNot(_.isEmpty)

      (check, value) match {
        case (Some("Employed"), Some(v)) => Right(value)
        case (Some("Employed"), None) => Left(List(FormError(key, Messages(s"error.required.$key"))))
        case _ => Right(None)
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  val form = Form(
    mapping(
      "parentsDegree" -> Mappings.nonEmptyTrimmedText("error.required.parentsDegree", 256),
      "employedParent" -> Mappings.nonEmptyTrimmedText("error.required.employmentStatus", 256),
      "parentsOccupation" -> of(employedDependentFormatter),
      "employee" -> of(employedDependentFormatter),
      "organizationSize" -> of(employedDependentFormatter),
      "supervise" -> of(employedDependentFormatter)
    )(Data.apply)(Data.unapply)
  )

  case class Data(
                   parentsDegree: String,
                   employedParent: String,
                   parentsOccupation: Option[String],
                   employee: Option[String],
                   organizationSize: Option[String],
                   supervise: Option[String]
                 ) {
    def exchange: Questionnaire = {
      val occupation = if (employedParent == "Employed") parentsOccupation else Some(employedParent)

      Questionnaire(List(
        Question(Messages("parentsDegree.question"), Answer(Some(parentsDegree), None, None)),
        Question(Messages("parentsOccupation.question"), Answer(occupation.sanitize, None, None)),
        Question(Messages("employee.question"), Answer(employee, None, None)),
        Question(Messages("organizationSize.question"), Answer(organizationSize, None, None)),
        Question(Messages("supervise.question"), Answer(supervise, None, None))
      ))
    }
  }
}
