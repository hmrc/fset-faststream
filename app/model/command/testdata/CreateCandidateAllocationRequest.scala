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

package model.command.testdata

import model.AllocationStatuses.AllocationStatus
import play.api.libs.json.{ Json, OFormat }

case class CreateCandidateAllocationRequest(id: String,
                                            status: Option[AllocationStatus],
                                            eventId: String,
                                            sessionId: String,
                                            version: Option[String]) extends CreateTestDataRequest

object CreateCandidateAllocationRequest {
  implicit val format: OFormat[CreateCandidateAllocationRequest] = Json.format[CreateCandidateAllocationRequest]
}
