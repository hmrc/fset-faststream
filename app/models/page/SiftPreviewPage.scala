/*
 * Copyright 2020 HM Revenue & Customs
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

package models.page

import connectors.exchange.referencedata.Scheme
import connectors.exchange.sift.SiftAnswersStatus.SiftAnswersStatus
import connectors.exchange.sift.{ GeneralQuestionsAnswers, SchemeSpecificAnswer, SiftAnswersStatus }

case class SiftPreviewPage(
  applicationId: String,
  status: SiftAnswersStatus,
  generalAnswers: Option[GeneralQuestionsAnswers],
  schemeAnswers: Map[Scheme, SchemeSpecificAnswer]
) {
  def areAnswersSubmitted: Boolean = status == SiftAnswersStatus.SUBMITTED
}

object SiftPreviewPage {
  def booleanToYesNo(o: Boolean): String = if(o) "Yes" else "No"
}
