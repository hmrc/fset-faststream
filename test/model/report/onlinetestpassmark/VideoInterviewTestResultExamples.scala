/*
 * Copyright 2021 HM Revenue & Customs
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

package model.report.onlinetestpassmark

import model.report.{ VideoInterviewQuestionTestResult, VideoInterviewTestResult }

object VideoInterviewTestResultExamples {

  lazy val Example1 = VideoInterviewTestResult(
    VideoInterviewQuestionTestResult(Some(1.0), Some(1.5)),
    VideoInterviewQuestionTestResult(Some(2.0), Some(3.5)),
    VideoInterviewQuestionTestResult(Some(3.0), Some(2.5)),
    VideoInterviewQuestionTestResult(Some(3.4), Some(2.5)),
    VideoInterviewQuestionTestResult(Some(3.0), Some(4.0)),
    VideoInterviewQuestionTestResult(Some(4.0), Some(4.0)),
    VideoInterviewQuestionTestResult(Some(4.0), Some(4.0)),
    VideoInterviewQuestionTestResult(Some(1.5), Some(3.5)),
    47.0
  )

  private def nextVideoInterviewQuestionScore = util.Random.shuffle(List(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0)).head
}
