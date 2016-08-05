/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.data.Forms._
import play.api.data.{Form, FormError}
import play.api.data.format.Formatter
import play.api.i18n.Messages

object AssistanceDetailsForm {

  def requiredFormatter(requiredKey: String, key: String) = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val requiredField: Option[String] = data.get(requiredKey)
      val keyField: Option[String] = data.get(key)

      (requiredField, keyField) match {
        case (Some("Yes"), None) => Left(List(FormError(key, Messages(s"error.$key.required"))))
        case (Some("Yes"), Some("")) => Left(List(FormError(key, Messages(s"error.$key.required"))))
        case _ => Right(keyField)
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  val form = Form(
    mapping(
      "hasDisability" -> Mappings.nonEmptyTrimmedText("error.hasDisability.required", 31),
      "hasDisabilityDescription" -> optional(Mappings.nonEmptyTrimmedText("error.hasDisabilityDescription.required", 2048)),
      "guaranteedInterview" -> of(requiredFormatter("hasDisability", "guaranteedInterview")),
      "needsSupportForOnlineAssessment" -> Mappings.nonEmptyTrimmedText("error.needsSupportForOnlineAssessment.required", 31),
      "needsSupportForOnlineAssessmentDescription" -> of(requiredFormatter("needsSupportForOnlineAssessment",
        "needsSupportForOnlineAssessmentDescription")),
      "needsSupportAtVenue" -> Mappings.nonEmptyTrimmedText("error.needsSupportAtVenue.required", 31),
      "needsSupportAtVenueDescription" -> of(requiredFormatter("needsSupportAtVenue", "needsSupportAtVenueDescription"))
    )(Data.apply)(Data.unapply)
  )

  case class Data(
                   hasDisability: String,
                   hasDisabilityDescription: Option[String],
                   guaranteedInterview: Option[String],
                   needsSupportForOnlineAssessment: String,
                   needsSupportForOnlineAssessmentDescription: Option[String],
                   needsSupportAtVenue: String,
                   needsSupportAtVenueDescription: Option[String]
  )

}
