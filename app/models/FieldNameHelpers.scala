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

package models

import _root_.connectors.exchange.referencedata.Scheme

object FieldNameHelpers {

  def createId(id: String, v: (String, String)) = { id + "-" + v._1.replace(" ", "_").replace("/", "_").replace("'", "_") }
  def createId(id: String, key: String) = { id + "-" + key.replace(" ", "_").replace("/", "_") }
  def createId(id: String, scheme: Scheme) = { id + "_" + scheme.id.value.replace(" ", "_").replace("/", "_").replace("'", "_") }
  def createId(id: String) = { id.replace(" ", "_").replace("/", "_") }




  def createDesc(id:String, label:String) = { id + "-" + label.replace(" ", "_").replace("/", "_") + "-description" }

  def formatId(id:String, v:(String,String)) = {
    createId(id.replace(" ", "_").replace("/", "_").replace("'", "_").replace(".", "_"), v)
  }

  def formatId(id:String) = { id.replace(" ", "_").replace("/", "_").replace("'", "_").replace(".", "_") }
}
