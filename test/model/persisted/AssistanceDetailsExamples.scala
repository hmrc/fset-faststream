/*
 * Copyright 2018 HM Revenue & Customs
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

package model.persisted

object AssistanceDetailsExamples {
  val OnlyDisabilityNoGisNoAdjustments = AssistanceDetails("Yes", Some(""), Some(false), Some(false), None,
    Some(false), None, None, None)
  val DisabilityGisAndAdjustments = AssistanceDetails("Yes", Some("disability description"), Some(true), Some(true),
    Some("online adjustment description"), Some(true), Some("venue adjustment description"), None, None)
}
