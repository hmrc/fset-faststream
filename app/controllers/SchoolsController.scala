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

package controllers

import play.api.libs.json.Json
import play.api.mvc._

object SchoolsController extends SchoolsController

case class School
(
  id: String,
  label: String
)



class SchoolsController extends Controller{
  implicit val schoolFormat = Json.format[School]

  def getSchools(term: String) = Action{
    val list = School("1","Hello") :: School("2","There") :: School("3", "Hi") :: Nil

    val results = list.filter(item => item.label.startsWith(term))
    Ok(Json.toJson(results))
  }
}
