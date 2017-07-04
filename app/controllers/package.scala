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

import model.persisted.eventschedules.SkillType
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.LocalDate
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.mvc.{PathBindable, QueryStringBindable}

package object controllers {

  object Binders {

    val pathDateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

    implicit val localDatePathBinder = new PathBindable.Parsing[LocalDate](
      parse = (dateVal: String) => LocalDate.parse(dateVal, pathDateFormat),
      serialize = _.toString(pathDateFormat),
      error = (m: String, e: Exception) => "Can't parse %s as LocalDate(%s): %s".format(m, pathDateFormat.toString, e.getMessage)
    )

    /*implicit object skillTypeQueryBinder extends QueryStringBindable.Parsing[SkillType](
       parse = SkillType.withName(_),
       serialize = _.toString,
       error = (m: String, e: Exception) => "Can't parse %s as SchemeId : %s".format(m, e.getMessage)
    )*/
    private def enumQueryBinder[E <: Enumeration](enum: E) = {
      new QueryStringBindable.Parsing[E#Value](
        parse = enum.withName(_),
        serialize = _.toString,
        error = (m: String, e: Exception) => "Can't parse %s as SchemeId : %s".format(m, e.getMessage)
      )
    }

    implicit val skillTypeQueryBinder = enumQueryBinder(SkillType)
  }
}
