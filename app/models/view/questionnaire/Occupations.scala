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

package models.view.questionnaire

// scalastyle:off line.size.limit
object Occupations {
  val seq = Seq(
    ("Traditional professional", "Accountant, solicitor, medical practitioner, scientist, civil/mechanical engineer"),
    ("Modern professional", "Teacher/lecturer, nurse, physiotherapist, social worker, welfare officer, artist, musician, police officer (sergeant or above), software designer"),
    ("Clerical (office work) and intermediate", "Secretary, personal assistant, clerical worker, office clerk, call centre agent, nursing auxiliary, nursery nurse"),
    ("Senior managers and administrators", "Usually responsible for planning, organising and co-ordinating work and for finance such as: finance manager, chief executive"),
    ("Technical and craft", "Motor mechanic, fitter, inspector, plumber, printer, tool maker, electrician, gardener, train driver"),
    ("Routine manual and service", "HGV driver, van driver, cleaner, porter, packer, sewing machinist, messenger, labourer, waiter / waitress, bar staff"),
    ("Semi-routine manual and service", "Postal worker, machine operative, security guard, caretaker, farm worker, catering assistant, receptionist, sales assistant"),
    ("Middle or junior managers", "Office manager, retail manager, bank manager, restaurant manager, warehouse manager, publican")
  )
}
