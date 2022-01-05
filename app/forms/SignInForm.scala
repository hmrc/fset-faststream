/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.Singleton
import mappings.Mappings._
import play.api.data.Form
import play.api.data.Forms._

@Singleton
class SignInForm {
  val passwordField = "signInPassword"

  val form = Form(
    mapping(
      "signIn" -> nonEmptyTrimmedText("error.required.signIn", 128),
      passwordField -> nonEmptyTrimmedText("error.required.password", 128),
      "route" -> optional(text)
    )(SignInForm.Data.apply)(SignInForm.Data.unapply)
  )

}

object SignInForm {
  case class Data(signIn: String, signInPassword: String, route: Option[String] = None)
}
