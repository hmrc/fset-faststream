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

package helpers

/**
 * Use this implicit when you have a standard component and you are using the helpers.
 *
 * usage:
 *
 *    Add this on top of your view:
 *
 *    @import helpers.CSRFieldConstructor._
 *
 *    ...
 *    @helpers.inputText(....)
 *
 */
object CSRFieldConstructor {
  import views.html.helper.FieldConstructor
  implicit val myFields = FieldConstructor(views.html.template.fieldTemplate.f)
}

/**
 * Use this implicit only in the case you have a custom html template and you don't need all the extra stuff.
 *
 * usage:
 *
 *    Add this on top of your view:
 *
 *    @import helpers.CSRSkinnyFieldConstructor._
 *
 *    ...
 *    @helpers.input(.....)
 *
 */
object CSRSkinnyFieldConstructor {
  import views.html.helper.FieldConstructor
  implicit val myFields = FieldConstructor(views.html.template.skinnyTemplate.f)
}
