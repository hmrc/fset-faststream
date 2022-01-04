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

package model

import model.testdata.candidate.CreateCandidateData.{AdjustmentDetailData, AdjustmentsData}
import play.api.libs.json.{Json, OFormat}

case class Adjustments(
  adjustments: Option[List[String]],
  adjustmentsConfirmed: Option[Boolean],
  etray: Option[AdjustmentDetail],
  video: Option[AdjustmentDetail]
)
object Adjustments {
  def apply(data: AdjustmentsData): Adjustments = {
    Adjustments(data.adjustments, data.adjustmentsConfirmed, data.etray.map(AdjustmentDetail(_)),
      data.video.map(AdjustmentDetail(_)))
  }
  implicit val adjustmentsFormat: OFormat[Adjustments] = Json.format[Adjustments]
}

case class AdjustmentDetail(
  timeNeeded: Option[Int] = None,
  percentage: Option[Int] = None,
  otherInfo: Option[String] = None,
  invigilatedInfo: Option[String] = None
)
object AdjustmentDetail {
  def apply(data: AdjustmentDetailData): AdjustmentDetail = {
    AdjustmentDetail(data.timeNeeded, data.percentage, data.otherInfo, data.invigilatedInfo)
  }
  implicit val adjustmentDetailFormat: OFormat[AdjustmentDetail] = Json.format[AdjustmentDetail]
}

case class TestAdjustment(percentage: Int)
object TestAdjustment {
  implicit val testAdjustmentFormat: OFormat[TestAdjustment] = Json.format[TestAdjustment]
}

case class AdjustmentsComment(
  comment: String
)
object AdjustmentsComment { implicit val adjustmentsCommentFormat: OFormat[AdjustmentsComment] = Json.format[AdjustmentsComment] }
