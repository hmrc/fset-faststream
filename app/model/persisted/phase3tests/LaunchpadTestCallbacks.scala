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

package model.persisted.phase3tests

import connectors.launchpadgateway.exchangeobjects.in._
import connectors.launchpadgateway.exchangeobjects.in.reviewed.ReviewedCallbackRequest
import play.api.libs.json.Json
import reactivemongo.bson.{BSONDocument, BSONHandler, Macros}

case class LaunchpadTestCallbacks(
                                 viewBrandedVideo: List[ViewBrandedVideoCallbackRequest] = Nil,
                                 setupProcess: List[SetupProcessCallbackRequest] = Nil,
                                 viewPracticeQuestion: List[ViewPracticeQuestionCallbackRequest] = Nil,
                                 question: List[ViewPracticeQuestionCallbackRequest] = Nil,
                                 finalCallback: List[FinalCallbackRequest] = Nil,
                                 finished: List[FinishedCallbackRequest] = Nil,
                                 reviewed: List[ReviewedCallbackRequest] = Nil
                     )

object LaunchpadTestCallbacks {
  implicit val launchpadTestCallbacksFormat = Json.format[LaunchpadTestCallbacks]
  implicit val bsonHandler: BSONHandler[BSONDocument, LaunchpadTestCallbacks] = Macros.handler[LaunchpadTestCallbacks]
}
