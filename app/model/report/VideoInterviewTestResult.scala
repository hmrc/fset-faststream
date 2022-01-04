/*
 * Copyright 2022 HM Revenue & Customs
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

package model.report

import play.api.libs.json.Json

case class VideoInterviewTestResult(question1: VideoInterviewQuestionTestResult,
                                    question2: VideoInterviewQuestionTestResult,
                                    question3: VideoInterviewQuestionTestResult,
                                    question4: VideoInterviewQuestionTestResult,
                                    question5: VideoInterviewQuestionTestResult,
                                    question6: VideoInterviewQuestionTestResult,
                                    question7: VideoInterviewQuestionTestResult,
                                    question8: VideoInterviewQuestionTestResult,
                                    overallTotal: Double) {
  def toStreamedContent = {
    question1.toStreamedContent :::
    question2.toStreamedContent :::
    question3.toStreamedContent :::
    question4.toStreamedContent :::
    question5.toStreamedContent :::
    question6.toStreamedContent :::
    question7.toStreamedContent :::
    question8.toStreamedContent :::
    List(Some(overallTotal.toString))
  }
}

case class VideoInterviewQuestionTestResult(capability: Option[Double], engagement: Option[Double]) {
  def toStreamedContent: List[Option[String]] = List(capability.map(_.toString), engagement.map(_.toString))
}

object VideoInterviewQuestionTestResult {
  implicit val videoInterviewQuestionTestResultFormats = Json.format[VideoInterviewQuestionTestResult]
}

object VideoInterviewTestResult {
  val empty = List(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None)
  implicit val videoInterviewTestResultFormats = Json.format[VideoInterviewTestResult]
}
