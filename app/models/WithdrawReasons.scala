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

object WithdrawReasons {
  val list = List(
    ("I've found another job elsewhere", false),
    ("I'm joining another apprenticeship scheme", false),
    ("I realised I wasn't eligible for an apprenticeship", false),
    ("My circumstances have changed", false),
    ("I have moved abroad", false),
    ("Other reason (provide details)", true)
  )
}
