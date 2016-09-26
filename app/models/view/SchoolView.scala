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

package models.view

import connectors.exchange.School
import scala.language.implicitConversions
import play.api.libs.json.Json


case class SchoolView(id: String, name: String, label: String)

object SchoolView {
  val limitResults = 15
  val narrowYourSearchHint = SchoolView("", "", "Please narrow down your search")
  implicit val schoolFormat = Json.format[SchoolView]

  implicit class SchoolImplicits(school:School) {
    def toSchoolView: SchoolView = {
      val address = school match {
        case _ if school.address1.exists(_.trim.nonEmpty) => school.address1
        case _ if school.address2.exists(_.trim.nonEmpty) => school.address2
        case _ if school.address3.exists(_.trim.nonEmpty) => school.address3
        case _ => None
      }
      val label = List(
        school.name,
        address.getOrElse(""),
        school.address4.getOrElse(""),
        school.postCode.getOrElse("")
      ).filter(_.nonEmpty).mkString(", ")

      SchoolView(school.id, school.name, label)
    }
  }
}
