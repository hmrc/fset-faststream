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
  implicit val adjustmentsFormat: OFormat[Adjustments] = Json.format[Adjustments]
  implicit def fromAdjustmentsData(data: AdjustmentsData) = {
    Adjustments(data.adjustments, data.adjustmentsConfirmed, data.etray.map(AdjustmentDetail.fromAdjustmentDetailData(_)),
      data.video.map(AdjustmentDetail.fromAdjustmentDetailData(_)))
  }
  implicit def fromAdjustmentsDataOpt(dataOpt: Option[AdjustmentsData]): Option[Adjustments] = {
    dataOpt.map(Adjustments.fromAdjustmentsData(_))
  }
}

case class AdjustmentDetail(
  timeNeeded: Option[Int] = None,
  percentage: Option[Int] = None,
  otherInfo: Option[String] = None,
  invigilatedInfo: Option[String] = None
)
object AdjustmentDetail {
  implicit val adjustmentDetailFormat: OFormat[AdjustmentDetail] = Json.format[AdjustmentDetail]
  implicit def fromAdjustmentDetailData(data: AdjustmentDetailData) = {
    AdjustmentDetail(data.timeNeeded, data.percentage, data.otherInfo, data.invigilatedInfo)
  }
  implicit def fromAdjustmentDetailDataOpt(dataOpt: Option[AdjustmentDetailData]): Option[AdjustmentDetail] = {
    dataOpt.map(AdjustmentDetail.fromAdjustmentDetailData(_))
  }
}

case class TestAdjustment(percentage: Int)
object TestAdjustment {
  implicit val testAdjustmentFormat: OFormat[TestAdjustment] = Json.format[TestAdjustment]
}

case class AdjustmentsComment(
  comment: String
)
object AdjustmentsComment { implicit val adjustmentsCommentFormat: OFormat[AdjustmentsComment] = Json.format[AdjustmentsComment] }
