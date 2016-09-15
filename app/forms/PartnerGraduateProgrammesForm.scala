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

import connectors.exchange.PartnerGraduateProgrammes
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages

object PartnerGraduateProgrammesForm {

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
        case (Some("Yes"), Seq()) => Left(List(FormError(key, Messages(s"error.$key.chooseone"))))
        case (Some("Yes"), Seq(opt)) if opt.isEmpty => Left(List(FormError(key, Messages(s"error.$key.chooseone"))))
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

  val form = Form(
    mapping(
      "interested" -> Mappings.nonEmptyTrimmedText("error.interested.required", 31),
      "partnerGraduateProgrammes" -> of(requiredListFormatter("interested", "partnerGraduateProgrammes"))
    )(Data.apply)(Data.unapply)
  )

  case class Data(interested: String, partnerGraduateProgrammes: Option[List[String]]) {

    def exchange = PartnerGraduateProgrammes(
      interested == "Yes",
      partnerGraduateProgrammes
    )

    def sanitizeData = {
      PartnerGraduateProgrammesForm.Data(
        interested,
        if (interested == "Yes") partnerGraduateProgrammes else None
      )
    }
  }

  object Data {
    def apply(pgp: PartnerGraduateProgrammes): PartnerGraduateProgrammesForm.Data =
      PartnerGraduateProgrammesForm.Data(if (pgp.interested) "Yes" else "No", pgp.partnerGraduateProgrammes)
  }

}
