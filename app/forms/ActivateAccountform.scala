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

import forms.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object ActivateAccountForm {

  val form = Form(
    mapping(
      "activation" -> (nonEmptyTrimmedText("activation.required", 7, "activation.wrong-format") verifying
        ("activation.wrong-format", value => value.matches("[\\w]{7}")))
    )(Data.apply)(Data.unapply)
  )

  case class Data(activationCode: String)
}
