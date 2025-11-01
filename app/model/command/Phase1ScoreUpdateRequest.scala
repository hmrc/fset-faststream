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

package model.command

import play.api.libs.json.{Format, Json}

case class Phase1ScoreUpdateRequest(applicationId: String,
                                    inventoryId: String,
                                    orderId: String,
                                    tScore: Double,
                                    rawScore: Double
                                   )

object Phase1ScoreUpdateRequest {
  implicit val jsonFormat: Format[Phase1ScoreUpdateRequest] = Json.format[Phase1ScoreUpdateRequest]
}

case class Phase1ScoreUpdateResponse(applicationId: String,
                                     inventoryId: String,
                                     orderId: String,
                                     tScore: Double,
                                     rawScore: Double,
                                     status: String
                                    )

object Phase1ScoreUpdateResponse {
  implicit val jsonFormat: Format[Phase1ScoreUpdateResponse] = Json.format[Phase1ScoreUpdateResponse]
  
  def apply(request: Phase1ScoreUpdateRequest, status: String) =
    new Phase1ScoreUpdateResponse(
      applicationId = request.applicationId,
      inventoryId = request.inventoryId,
      orderId = request.orderId,
      tScore = request.tScore,
      rawScore = request.rawScore,
      status = status
    )
}
