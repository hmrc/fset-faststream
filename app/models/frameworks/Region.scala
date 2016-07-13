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

package models.frameworks

import play.api.libs.json.{ Format, Json }

case class Region(name: String, locations: List[Location])

object Region {
  implicit val jsonFormat: Format[Region] = Json.format[Region]

  def toJson(regions: List[Region]): String =
    s"{${jsonList(regions.map(reg => reg.name -> jsonList(reg.locations.map(loc => loc.name -> jsonArr(loc.frameworks)))))}}"

  private def jsonList(elements: List[(String, String)]) = elements.map { case (n, v) => jsonEl(n, v) }.mkString(",")

  private def jsonEl(key: String, value: String) =
    if (value.startsWith("[")) s"${q(key)}:$value" else s"${q(key)}:{$value}"

  private def jsonArr(l: List[String]) = s"[${l.map(q).mkString(",")}]"

  private def q(s: String) = "\"" + s + "\""

}
