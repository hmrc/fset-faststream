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
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages

object AssistanceForm {

  def requiredFormatter(requiredKey: String, key: String) = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val requiredField: Option[String] = data.get(requiredKey)
      val keyField: Option[String] = data.get(key)

      (requiredField, keyField) match {
        case (Some("Yes"), None) => Left(List(FormError(key, Messages(s"$key.chooseone"))))
        case (Some("Yes"), Some("")) => Left(List(FormError(key, Messages(s"$key.chooseone"))))
        case _ => Right(keyField)
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  def requiredListFormatter(requiredKey: String, key: String) = new Formatter[Option[List[String]]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[List[String]]] = {

      def indexes(key: String, data: Map[String, String]): Seq[Int] = {
        val KeyPattern = ("^" + java.util.regex.Pattern.quote(key) + """\[(\d+)\].*$""").r
        data.toSeq.collect { case (KeyPattern(index), _) => index.toInt }.sorted.distinct
      }

      val i = indexes(key, data)
      val requiredField: Option[String] = data.get(requiredKey)
      val keyField: List[String] = indexes(key, data).flatMap(i => data.get(s"$key[$i]")).toList

      (requiredField, keyField) match {
        case (Some("Yes"), Seq()) => Left(List(FormError(key, Messages(s"$key.chooseone"))))
        case (Some("Yes"), Seq(opt)) if opt.isEmpty => Left(List(FormError(key, Messages(s"$key.chooseone"))))
        case _ => Right(Some(keyField))
      }
    }

    override def unbind(key: String, value: Option[List[String]]): Map[String, String] = {
      val sk: Option[List[(String, String)]] = value.map { v =>
        v.zipWithIndex.map { case (item, i) => s"$key[$i]" -> item }
      }

      val ssk: Option[Map[String, String]] = sk map { lst => lst.foldLeft(Map.empty[String, String])((z, a) => z + a) }

      ssk.getOrElse(Map.empty[String, String])
    }
  }

  // todo - find a better way to do this
  def alLeastOneformatter(key: String) = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val needsAdjustment: Option[String] = data.get("needsAdjustment")
      val extraTime: Option[String] = data.get("extraTime")
      val screenMagnification: Option[String] = data.get("screenMagnification")
      val printCopies: Option[String] = data.get("printCopies")
      val otherAdjustments: Option[String] = data.get("otherAdjustments")

      (needsAdjustment, extraTime, screenMagnification, printCopies, otherAdjustments) match {
        case (Some("Yes"), None, None, None, Some("")) => Left(List(
          FormError("otherAdjustments", Messages("otherAdjustments.chooseone"))
        ))
        case _ =>
          val keyField: Option[String] = data.get(key)
          Right(keyField.map(_.trim))
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.map(_.trim).getOrElse(""))
  }

  val form = Form(
    mapping(
      "needsAssistance" -> Mappings.nonEmptyTrimmedText("error.needsassistance.required", 18),
      "typeOfdisability" -> of(requiredListFormatter("needsAssistance", "typeOfdisability")),
      "detailsOfdisability" -> optional(Mappings.nonEmptyTrimmedText("error.required.detailsOfdisability", 4000)),
      "guaranteedInterview" -> of(requiredFormatter("needsAssistance", "guaranteedInterview")),
      "needsAdjustment" -> Mappings.nonEmptyTrimmedText("needsAdjustment.chooseone", 3),
      "typeOfAdjustments" -> of(requiredListFormatter("needsAdjustment", "typeOfAdjustments")),
      "otherAdjustments" -> optional(Mappings.nonEmptyTrimmedText("otherAdjustments.chooseone", 4000)),
      "campaignReferrer" -> Mappings.optionalTrimmedText(64),
      "campaignOther" -> Mappings.optionalTrimmedText(256)
    )(Data.apply)(Data.unapply)
  )

  case class Data(
    needsAssistance: String,
    typeOfdisability: Option[List[String]],
    detailsOfdisability: Option[String],
    guaranteedInterview: Option[String],
    needsAdjustment: String,
    typeOfAdjustments: Option[List[String]],
    otherAdjustments: Option[String],
    campaignReferrer: Option[String],
    campaignOther: Option[String]
  )

}
