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

package models

/**
 * Created by holgersteinhauer on 16/12/2015.
 */
object AdjustmentTypes {
  val seq = Seq(
    ("time extension", "Time extension, eg 25% or 30%"),
    ("coloured paper", "Coloured paper"),
    ("braille test paper", "Braille test paper"),
    ("room alone", "Room alone"),
    ("rest breaks", "Rest breaks"),
    ("reader/assistant", "Reader/assistant"),
    ("stand up and move around", "Stand up and move around"),
    ("other", "Other")
  )
}
