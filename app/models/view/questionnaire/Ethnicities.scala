/*
 * Copyright 2019 HM Revenue & Customs
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

package models.view.questionnaire

import scala.collection.immutable.ListMap

object Ethnicities {
  val map = ListMap(
    "White" -> List(
      ("English/Welsh/Scottish/Northern Irish/British", false),
      ("Irish", false),
      ("Gypsy or Irish Traveller", false),
      ("Other White background", true)
    ),
    "Mixed/Multiple ethnic groups" -> List(
      ("White and Black Caribbean", false),
      ("White and Black African", false),
      ("White and Asian", false),
      ("Other mixed/multiple ethnic background", true)
    ),
    "Asian/Asian British" -> List(
      ("Indian", false),
      ("Pakistani", false),
      ("Bangladeshi", false),
      ("Chinese", false),
      ("Other Asian background", true)
    ),
    "Black/African/Caribbean/Black British" -> List(
      ("African", false),
      ("Caribbean", false),
      ("Other Black/African/Caribbean background", true)
    ),
    "Other ethnic group" -> List(
      ("Arab", false),
      ("Other ethnic group", true)
    )
  )
}
