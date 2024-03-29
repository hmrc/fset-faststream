/*
 * Copyright 2023 HM Revenue & Customs
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

package model.persisted.phase3tests

import connectors.launchpadgateway.exchangeobjects.in._
import connectors.launchpadgateway.exchangeobjects.in.reviewed.{ReviewedCallbackRequest, ReviewedCallbackRequestExchange}
import play.api.libs.json.{Json, OFormat}

case class LaunchpadTestCallbacks(
                                 viewBrandedVideo: List[ViewBrandedVideoCallbackRequest] = Nil,
                                 setupProcess: List[SetupProcessCallbackRequest] = Nil,
                                 viewPracticeQuestion: List[ViewPracticeQuestionCallbackRequest] = Nil,
                                 question: List[ViewPracticeQuestionCallbackRequest] = Nil,
                                 finalCallback: List[FinalCallbackRequest] = Nil,
                                 finished: List[FinishedCallbackRequest] = Nil,
                                 reviewed: List[ReviewedCallbackRequest] = Nil
                     ) {
  def getLatestReviewed: Option[ReviewedCallbackRequest] =
    reviewed.sortWith { (r1, r2) => r1.received.isAfter(r2.received) }.headOption

  def toExchange = LaunchpadTestCallbacksExchange(
    viewBrandedVideo,
    setupProcess,
    viewPracticeQuestion,
    question,
    finalCallback,
    finished,
    reviewed.map(_.toExchange)
  )
}

object LaunchpadTestCallbacks {
  implicit val launchpadTestCallbacksFormat: OFormat[LaunchpadTestCallbacks] = Json.format[LaunchpadTestCallbacks]
}

case class LaunchpadTestCallbacksExchange(
                                   viewBrandedVideo: List[ViewBrandedVideoCallbackRequest] = Nil,
                                   setupProcess: List[SetupProcessCallbackRequest] = Nil,
                                   viewPracticeQuestion: List[ViewPracticeQuestionCallbackRequest] = Nil,
                                   question: List[ViewPracticeQuestionCallbackRequest] = Nil,
                                   finalCallback: List[FinalCallbackRequest] = Nil,
                                   finished: List[FinishedCallbackRequest] = Nil,
                                   reviewed: List[ReviewedCallbackRequestExchange] = Nil
                                         )

object LaunchpadTestCallbacksExchange {
  implicit val launchpadTestCallbacksFormat: OFormat[LaunchpadTestCallbacksExchange] = Json.format[LaunchpadTestCallbacksExchange]
}
