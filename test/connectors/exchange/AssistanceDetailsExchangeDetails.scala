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

import java.util.UUID

import connectors.exchange.ProgressResponse
import connectors.exchange.AssistanceDetailsExchange

object AssistanceDetailsExchangeExamples {
  val OnlyDisabilityNoGisNoAdjustments = AssistanceDetailsExchange("Yes", Some(""), Some(false), false, None, false, None)
  val DisabilityGisAndAdjustments = AssistanceDetailsExchange("Yes", Some("disability description"), Some(true), true,
    Some("online adjustment description xxx"), true, Some("venue adjustment description yyy"))
}