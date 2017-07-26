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

import models.view.WithdrawReasons
import play.api.Play.current
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._

object SchemeSpecificQuestionsForm {

  val form = Form(
    mapping(
      "schemeAnswer" -> of(requiredFormatterWithMaxLengthCheck("schemeAnswer", "reason", Some(64)))
    )(Data.apply)(Data.unapply)
  )

  case class Data(
                   schemeAnswer: Option[String]
                 )
}
