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

package services.testdata.examples

import model.{ AdjustmentDetail, Adjustments }

object AdjustmentsExamples {
  val InvigilatedETrayAdjustments = Adjustments(
    Some(List("etrayInvigilated")), Some(true), Some(AdjustmentDetail(invigilatedInfo = Some("invigilated e-tray " +
      "approved"), percentage = Some(50))), None
  )
  val ETrayTimeExtensionAdjustments = Adjustments(
    Some(List("etrayTimeExtension")), Some(true), Some(AdjustmentDetail(invigilatedInfo = Some("time extension info"))), None
  )
  val EmptyAdjustments = Adjustments(None, adjustmentsConfirmed = Some(true), etray = None, video = None)

  val ETrayTimeAdjustments = Adjustments(
    adjustments = Some(List("etrayTimeExtension")), adjustmentsConfirmed = None,
    etray = Some(AdjustmentDetail(timeNeeded = Some(10), percentage = Some(50))),
    video = None
  )
}
